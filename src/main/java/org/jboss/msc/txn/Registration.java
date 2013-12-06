/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.msc.txn;

import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jboss.msc.service.ServiceName;

/**
 * A service registration.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class Registration extends TransactionalObject {

    private static final AttachmentKey<Boolean> VALIDATE_TASK = AttachmentKey.create();

    /**
     * Indicates if registry is enabled
     */
    private static final int REGISTRY_ENABLED =       0x40000000;
    /**
     * Indicates if registration is removed (true only when registry is removed).
     */
    private static final int REMOVED  =      0x20000000;
    /**
     * The number of dependent instances which place a demand-to-start on this registration.  If this value is > 0,
     * propagate a demand to the instance, if any.
     */
    private static final int DEMANDED_MASK = 0x1fffffff;

    /** The registration name */
    private final ServiceName serviceName;
    /**
     * The service controller.
     */
    private volatile ServiceControllerImpl<?> controller;
    /**
     * Incoming dependencies, i.e., dependent services.
     */
    private final Set<DependencyImpl<?>> incomingDependencies = new CopyOnWriteArraySet<DependencyImpl<?>>();
    /**
     * State.
     */
    private int state;


    Registration(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    ServiceName getServiceName() {
        return serviceName;
    }

    ServiceControllerImpl<?> getController() {
        return controller;
    }

    boolean resetController(final ServiceControllerImpl<?> serviceController, final Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        assert controller == null;
        return setController(serviceController, transaction, null);
    }

    boolean setController(final ServiceControllerImpl<?> serviceController, final Transaction transaction) {
        return setController(serviceController, transaction, transaction.getTaskFactory());
    }

    private boolean setController(final ServiceControllerImpl<?> serviceController, final Transaction transaction, final TaskFactory taskFactory) {
        lockWrite(transaction);
        final boolean demanded;
        synchronized (this) {
            if (this.controller != null) {
                return false;
            }
            this.controller = serviceController;
            demanded = (state & DEMANDED_MASK) > 0;
        }
        if (demanded) {
            serviceController.demand(transaction, taskFactory);
        }
        if (Bits.anyAreSet(state, REMOVED)) {
            throw TXN.removedServiceRegistry(); // display registry removed message to user, as this scenario only occurs when registry has been removed
        }
        if (Bits.anyAreSet(state, REGISTRY_ENABLED)) {
            serviceController.enableRegistry(transaction);
        } else {
            serviceController.disableRegistry(transaction);
        }
        return true;
    }

    void clearController(final Transaction transaction) {
        lockWrite(transaction);
        installDependenciesValidateTask(transaction);
        synchronized (this) {
            this.controller = null;
        }
    }

    <T> void addIncomingDependency(final Transaction transaction, final DependencyImpl<T> dependency) {
        lockWrite(transaction);
        installDependenciesValidateTask(transaction);
        final TaskController<Boolean> startTask;
        final boolean up;
        synchronized (this) {
            incomingDependencies.add(dependency);
            if (controller == null) {
                up = false;
                startTask = null;
            } else {
                startTask = controller.getStartTask(transaction);
                up = startTask != null || controller.getState() == ServiceControllerImpl.STATE_UP;
            }
        }
        if (up) {
            dependency.dependencyUp(transaction, transaction.getTaskFactory(), startTask);
        }
    }

    void removeIncomingDependency(final Transaction transaction, final DependencyImpl<?> dependency) {
        lockWrite(transaction);
        assert incomingDependencies.contains(dependency);
        incomingDependencies.remove(dependency);
    }

    void serviceUp(final Transaction transaction, final TaskFactory taskFactory, final TaskController<Boolean> startTask) {
        for (DependencyImpl<?> incomingDependency: incomingDependencies) {
            incomingDependency.dependencyUp(transaction, taskFactory, startTask);
        }
    }

    void serviceDown(final Transaction transaction, final TaskFactory taskFactory, final List<TaskController<?>> tasks) {
        for (DependencyImpl<?> incomingDependency: incomingDependencies) {
            final TaskController<?> task = incomingDependency.dependencyDown(transaction, taskFactory);
            if (task != null) {
                tasks.add(task);
            }
        }
    }

    void addDemand(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            controller = this.controller;
            if (((++ state) & DEMANDED_MASK) > 1) {
                return;
            }
        }
        if (controller != null) {
            controller.demand(transaction, taskFactory);
        }
    }

    void removeDemand(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            controller = this.controller;
            if (((--state) & DEMANDED_MASK) > 0) {
                return;
            }
        }
        if (controller != null) {
            controller.undemand(transaction, taskFactory);
        }
    }

    void remove(Transaction transaction) {
        lockWrite(transaction);
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            controller = this.controller;
            if (Bits.anyAreSet(state, REMOVED)) return;
            state = state | REMOVED;
        }
        if (controller != null) {
            controller.remove(transaction);
        }
    }

    void disableRegistry(Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreClear(state,  REGISTRY_ENABLED)) return;
            state = state & ~REGISTRY_ENABLED;
            controller = this.controller;
        }
        if (controller != null) {
            controller.disableRegistry(transaction);
        }
    }

    void enableRegistry(Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreSet(state, REGISTRY_ENABLED)) return;
            state = state | REGISTRY_ENABLED;
            controller = this.controller;
        }
        if (controller != null) {
            controller.enableRegistry(transaction);
        }
    }

    void installDependenciesValidateTask(final Transaction transaction) {
        if (transaction.putAttachment(VALIDATE_TASK, Boolean.TRUE) != null) return;
        ((TaskBuilderImpl<Void>)transaction.newTask()).setValidatable(new Validatable() {
            @Override
            public void validate(final ValidateContext context) {
                try {
                    synchronized (Registration.this) {
                        for (final DependencyImpl<?> incomingDependency : incomingDependencies) {
                            incomingDependency.validate(controller, context);
                        }
                    }
                } finally {
                    context.complete();
                }
            }
        }).release();
    }

    @Override
    Object takeSnapshot() {
        return new Snapshot();
    }

    @Override
    void revert(final Object snapshot) {
        ((Snapshot)snapshot).apply();
    }

    private final class Snapshot {

        private final ServiceControllerImpl<?> controller;
        private final Collection<DependencyImpl<?>> incomingDependencies;
        private final int state;

        // take snapshot
        public Snapshot() {
            controller = Registration.this.controller;
            incomingDependencies = new ArrayList<DependencyImpl<?>>(Registration.this.incomingDependencies.size());
            incomingDependencies.addAll(Registration.this.incomingDependencies);
            state = Registration.this.state;
        }

        // revert ServiceController state to what it was when snapshot was taken; invoked on rollback or abort
        public void apply() {
            Registration.this.controller = controller;
            Registration.this.state = state;
            Registration.this.incomingDependencies.clear();
            Registration.this.incomingDependencies.addAll(incomingDependencies);
        }
    }
}
