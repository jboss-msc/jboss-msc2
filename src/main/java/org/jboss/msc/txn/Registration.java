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

import org.jboss.msc.service.ServiceName;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.jboss.msc._private.MSCLogger.TXN;
import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * A service registration.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class Registration {

    private static final AttachmentKey<RequiredDependenciesCheck> REQUIRED_DEPENDENCIES_CHECK_TASK = AttachmentKey.create();

    /**
     * Indicates if registry is enabled
     */
    private static final int REGISTRY_ENABLED =       0x40000000;
    /**
     * Indicates if registration is removed (true only when registry is removed).
     */
    private static final int REMOVED  =      0x20000000;
    /**
     * Indicates this registration is scheduled to start or up.
     */
    private static final int UP = 0x8000000;
    /** 
     * Indicates this registration has failed to start.
     */
    private static final int FAILED = 0x4000000;
    /**
     * The number of dependent instances which place a demand-to-start on this registration.  If this value is > 0,
     * propagate a demand to the instance, if any.
     */
    private static final int DEMANDED_MASK = 0x3ffffff;


    /** The registration name */
    private final ServiceName serviceName;
    /** Associated transaction controller */
    final TransactionController txnController;
    /** Associated controller */
    final AtomicReference<ServiceControllerImpl<?>> holderRef = new AtomicReference<>();

    /**
     * Incoming dependencies, i.e., dependent services.
     */
    final Set<DependencyImpl<?>> incomingDependencies = new HashSet<>();
    /**
     * State.
     */
    private int state;

    Registration(final ServiceName serviceName, final TransactionController txnController) {
        this.serviceName = serviceName;
        this.txnController = txnController;
    }

    ServiceName getServiceName() {
        return serviceName;
    }

    ServiceControllerImpl<?> getController() {
        return holderRef.get();
    }

    /**
     * Installs a service,
     *
     * @param transaction       the active transaction
     * @param taskFactory       the task factory
     */
    void installService(final Transaction transaction, final TaskFactory taskFactory) {
        final ServiceControllerImpl<?> serviceController;
        synchronized (this) {
            serviceController = holderRef.get();
        }
        if (Bits.anyAreSet(state, REMOVED)) {
            throw TXN.removedServiceRegistry(); // display registry removed message to user, as this scenario only occurs when registry has been removed
        }
        if (Bits.anyAreSet(state, REGISTRY_ENABLED)) {
            serviceController.enableRegistry(transaction, taskFactory);
        } else {
            serviceController.disableRegistry(transaction, taskFactory);
        }
    }

    void clearController(final Transaction transaction, final TaskFactory taskFactory) {
        installDependenciesValidateTask(transaction, taskFactory);
        synchronized (this) {
            holderRef.set(null);
        }
    }

    <T> void addIncomingDependency(final Transaction transaction, final DependencyImpl<T> dependency) {
        installDependenciesValidateTask(transaction, getAbstractTransaction(transaction).getTaskFactory());
        final TaskController<?> startTask;
        final boolean up;
        synchronized (this) {
            incomingDependencies.add(dependency);
            up = Bits.anyAreSet(state,  UP);
            startTask = up? holderRef.get().getStartTask(transaction): null;
            if (up) {
                dependency.dependencyUp(transaction, getAbstractTransaction(transaction).getTaskFactory(), startTask);
            }
        }
    }

    void removeIncomingDependency(final DependencyImpl<?> dependency) {
        synchronized (this) {
            incomingDependencies.remove(dependency);
        }
    }

    void serviceStarting(final Transaction transaction, final TaskFactory taskFactory, final TaskController<?> startTask) {
        synchronized (this) {
            // handle out of order notifications
            if (Bits.anyAreSet(state, FAILED)) {
                return;
            }
            state = state | UP;
            for (final DependencyImpl<?> incomingDependency: incomingDependencies) {
                incomingDependency.dependencyUp(transaction, taskFactory, startTask);
            }
        }
    }

    void serviceFailed(final Transaction transaction, final TaskFactory taskFactory, final List<TaskController<?>> tasks) {
        synchronized (this) {
            state = state | FAILED;
            // handle out of order notifications
            if (Bits.anyAreSet(state, UP)) {
                state = state & ~UP;
                for (final DependencyImpl<?> incomingDependency: incomingDependencies) {
                    final TaskController<?> task = incomingDependency.dependencyDown(transaction, taskFactory);
                    if (task != null) {
                        tasks.add(task);
                    }
                }
            }
        }
    }

    void serviceStopping(final Transaction transaction, final TaskFactory taskFactory, final List<TaskController<?>> tasks) {
        synchronized (this) {
            state = state & ~(UP | FAILED);
            for (DependencyImpl<?> incomingDependency: incomingDependencies) {
                final TaskController<?> task = incomingDependency.dependencyDown(transaction, taskFactory);
                if (task != null) {
                    tasks.add(task);
                }
            }
        }
    }

    void addDemand(final Transaction transaction, final TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (((++ state) & DEMANDED_MASK) > 1) {
                return;
            }
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.demand(transaction, taskFactory);
        }
    }

    void removeDemand(final Transaction transaction, final TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (((--state) & DEMANDED_MASK) > 0) {
                return;
            }
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.undemand(transaction, taskFactory);
        }
    }

    void remove(final Transaction transaction, final TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            state = state | REMOVED;
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.remove(transaction, taskFactory);
        }
    }

    void reinstall() {
        synchronized (this) {
            state = state & ~REMOVED;
        }
    }

    void disableRegistry(Transaction transaction, TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreClear(state,  REGISTRY_ENABLED)) return;
            state = state & ~REGISTRY_ENABLED;
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.disableRegistry(transaction, taskFactory);
        }
    }

    void enableRegistry(Transaction transaction, TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreSet(state, REGISTRY_ENABLED)) return;
            state = state | REGISTRY_ENABLED;
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.enableRegistry(transaction, taskFactory);
        }
    }

    void installDependenciesValidateTask(final Transaction transaction, final TaskFactory taskFactory) {
        if (taskFactory == null) {
            return;
        }
        RequiredDependenciesCheck task = transaction.getAttachmentIfPresent(REQUIRED_DEPENDENCIES_CHECK_TASK);
        if (task == null) {
            task = new RequiredDependenciesCheck(transaction.getReport());
            final RequiredDependenciesCheck appearing = transaction.putAttachmentIfAbsent(REQUIRED_DEPENDENCIES_CHECK_TASK, task);
            if (appearing == null) {
                getAbstractTransaction(transaction).addListener(task);
            } else {
                task = appearing;
            }
        }
        task.addRegistration(this);
    }

    private static final class RequiredDependenciesCheck implements PrepareCompletionListener {

        private Set<Registration> registrations = Collections.newSetFromMap(new IdentityHashMap<Registration, Boolean>());
        private final ProblemReport report;

        RequiredDependenciesCheck(final ProblemReport report) {
            this.report = report;
        }

        void addRegistration(final Registration registration) {
            synchronized (this) {
                if (registrations != null) {
                    registrations.add(registration);
                    return;
                }
            }
            throw new InvalidTransactionStateException();
        }

        @Override
        public void transactionPrepared() {
            final Set<Registration> registrations;
            synchronized (this) {
                registrations = this.registrations;
                this.registrations = null;
            }
            for (final Registration registration : registrations) {
                synchronized (registration) {
                    for (final DependencyImpl<?> dependency : registration.incomingDependencies) {
                        dependency.validate(report);
                    }
                }
            }
        }
    }
}
