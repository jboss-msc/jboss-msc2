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

import static org.jboss.msc.txn.ServiceControllerImpl.STATE_UP;

import org.jboss.msc.problem.ProblemReport;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.util.AttachmentKey;

import java.util.HashSet;
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
    private static final int REGISTRY_ENABLED = 0x40000000;
    /**
     * Indicates if registration is removed (true only when registry is removed).
     */
    private static final int REMOVED = 0x20000000;
    /**
     * The number of dependent instances which place a demand-to-start on this registration.  If this value is > 0,
     * propagate a demand to the instance, if any.
     */
    private static final int DEMANDED_MASK = 0x3ffffff;


    /** The registration name */
    private final ServiceName serviceName;
    /** Associated transaction controller */
    final ServiceRegistryImpl registry;
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

    Registration(final ServiceName serviceName, final ServiceRegistryImpl registry) {
        this.serviceName = serviceName;
        this.registry = registry;
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
     */
    void installService(final Transaction transaction) {
        final ServiceControllerImpl<?> serviceController;
        boolean registryEnabled = false;
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) {
                throw TXN.removedServiceRegistry(); // display registry removed message to user, as this scenario only occurs when registry has been removed
            }
            serviceController = holderRef.get();
            if (Bits.anyAreSet(state, REGISTRY_ENABLED)) {
                registryEnabled = true;
            }
        }
        if (registryEnabled) {
            serviceController.enableRegistry(transaction);
        } else {
            serviceController.disableRegistry(transaction);
        }
    }

    void clearController(final Transaction transaction) {
        installDependenciesValidateTask(transaction);
        synchronized (this) {
            holderRef.set(null);
        }
    }

    <T> void addIncomingDependency(final Transaction transaction, final DependencyImpl<T> dependency) {
        installDependenciesValidateTask(transaction);
        synchronized (this) {
            incomingDependencies.add(dependency);
            final ServiceControllerImpl<?> controller = holderRef.get();
            final boolean up = controller != null && controller.getState() == STATE_UP;
            if (up) {
                dependency.dependencyUp(transaction);
            }
        }
    }

    void removeIncomingDependency(final DependencyImpl<?> dependency) {
        synchronized (this) {
            incomingDependencies.remove(dependency);
        }
    }

    void serviceUp(final Transaction transaction) {
        synchronized (this) {
            for (final DependencyImpl<?> incomingDependency: incomingDependencies) {
                incomingDependency.dependencyUp(transaction);
            }
        }
    }

    void serviceDown(final Transaction transaction) {
        synchronized (this) {
            for (DependencyImpl<?> incomingDependency: incomingDependencies) {
                incomingDependency.dependencyDown(transaction);
            }
        }
    }

    void addDemand(final Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            if (((++ state) & DEMANDED_MASK) > 1) {
                return;
            }
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.demand(transaction);
        }
    }

    void removeDemand(final Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            if (((--state) & DEMANDED_MASK) > 0) {
                return;
            }
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.undemand(transaction);
        }
    }

    void remove(final Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            state = state | REMOVED;
            controller = holderRef.get();
        }
        if (controller != null) {
            controller._remove(transaction, null);
        }
    }

    void disableRegistry(Transaction transaction) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreClear(state,  REGISTRY_ENABLED)) return;
            state = state & ~REGISTRY_ENABLED;
            controller = holderRef.get();
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
            controller = holderRef.get();
        }
        if (controller != null) {
            controller.enableRegistry(transaction);
        }
    }

    void installDependenciesValidateTask(final Transaction transaction) {
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

    void serviceInstalled() {
        registry.serviceInstalled();
    }

    void serviceRemoved() {
        registry.serviceRemoved();
    }

    TransactionController getTransactionController() {
        return registry.getTransactionController();
    }

    private static final class RequiredDependenciesCheck implements PrepareCompletionListener {

        private Set<Registration> registrations = new IdentityHashSet<>();
        private final ProblemReport report;

        RequiredDependenciesCheck(final ProblemReport report) {
            this.report = report;
        }

        void addRegistration(final Registration registration) {
            synchronized (this) {
                registrations.add(registration);
            }
        }

        @Override
        public void transactionPrepared() {
            final Set<Registration> registrations;
            synchronized (this) {
                registrations = this.registrations;
                this.registrations = new IdentityHashSet<>();
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
