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

import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.ServiceName;

/**
 * A service registration.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class Registration {

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
     * Indicates this registration is under service installation process. During this process, controller is null
     * (i.e., no demand/disable messages are forwarded to controller), but not other controller can start installation.
     * @see #preinstallService(Transaction)
     */
    private static final int INSTALLATION_LOCKED = 0x10000000;
    /**
     * The number of dependent instances which place a demand-to-start on this registration.  If this value is > 0,
     * propagate a demand to the instance, if any.
     */
    private static final int DEMANDED_MASK = 0xfffffff;


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
    /**
     * Transactional lock.
     */
    private TransactionalLock lock = new TransactionalLock();

    Registration(ServiceName serviceName) {
        this.serviceName = serviceName;
    }

    ServiceName getServiceName() {
        return serviceName;
    }

    ServiceControllerImpl<?> getController() {
        return controller;
    }

    /**
     * Locks this registration for a {@link #installService(ServiceControllerImpl, Transaction) service installation},
     * thus guaranteeing that not more than one service will be installed at the same registration.<br>
     * If there is already a service installed, or if this registration is already locked for another service
     * installation, throws a {@code DuplicateServiceException}.
     * 
     * @param transaction   the active transaction
     * @throws DuplicateServiceException if there is a service installed at this registration
     */
    void preinstallService(final Transaction transaction) throws DuplicateServiceException {
        lock.lockSynchronously(transaction);
        synchronized (this) {
            if (controller != null || Bits.anyAreSet(state,  INSTALLATION_LOCKED)) {
                throw SERVICE.duplicateService(serviceName);
            }
            state = state | INSTALLATION_LOCKED;
        }
    }

    /**
     * Completes a service installation. This method can only be called after {@link #preinstallService(Transaction)},
     * to guarantee that only a single controller will be installed at this registration.
     * 
     * @param serviceController the controller
     * @param transaction       the active transaction
     */
    void installService(final ServiceControllerImpl<?> serviceController, final Transaction transaction) {
        installService(serviceController, transaction, transaction.getTaskFactory());
    }

    /**
     * Reinstall the service into this registration (invoked on revert).
     * 
     * @param serviceController the controller
     * @param transaction       the active transaction
     */
    void reinstallService(final ServiceControllerImpl<?> serviceController, final Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        assert controller == null;
        preinstallService(transaction);
        installService(serviceController, transaction, null);
    }

    private void installService(final ServiceControllerImpl<?> serviceController, final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doInstallService(serviceController, transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doInstallService(serviceController, transaction, taskFactory);
                }
            });
        }
    }

    private void doInstallService(ServiceControllerImpl<?> serviceController, Transaction transaction, TaskFactory taskFactory) {
        final boolean demanded;
        synchronized (this) {
            assert this.controller == null && Bits.anyAreSet(state,  INSTALLATION_LOCKED): "Installation not properly initiated, invoke preinstallService first.";
            this.controller = serviceController;
            state = state & ~INSTALLATION_LOCKED;
            demanded = (state & DEMANDED_MASK) > 0;
        }
        if (demanded) {
            serviceController.demand(transaction, taskFactory);
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
        if (lock.tryLock(transaction)) {
            doClearController(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doClearController(transaction, taskFactory);
                }
            });
        }
    }

    private void doClearController(Transaction transaction, TaskFactory taskFactory) {
        installDependenciesValidateTask(transaction, taskFactory);
        synchronized (this) {
            this.controller = null;
        }
    }

    <T> void addIncomingDependency(final Transaction transaction, final DependencyImpl<T> dependency) {
        if (lock.tryLock(transaction)) {
            doAddIncomingDependency(transaction, dependency);
        } else {
            lock.lockAsynchronously(transaction, new LockListener(){
                public void lockAcquired() {
                    doAddIncomingDependency(transaction, dependency);
                }
            });
        }
    }

    private void doAddIncomingDependency(Transaction transaction, DependencyImpl<?> dependency) {
        installDependenciesValidateTask(transaction, transaction.getTaskFactory());
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
        if (lock.tryLock(transaction)) {
            doRemoveIncomingDependency(dependency);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doRemoveIncomingDependency(dependency);
                }
            });
        }
    }

    private void doRemoveIncomingDependency(DependencyImpl<?> dependency) {
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

    void addDemand(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doAddDemand(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doAddDemand(transaction, taskFactory);
                }
            });
        }
    }

    void doAddDemand(Transaction transaction, TaskFactory taskFactory) {
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

    void removeDemand(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doRemoveDemand(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                 public void lockAcquired() {
                    doRemoveDemand(transaction, taskFactory);
                }});
        }
        
    }

    private void doRemoveDemand(Transaction transaction, TaskFactory taskFactory) {
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

    void remove(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doRemove(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doRemove(transaction, taskFactory);
                }});
        }
    }

    private void doRemove(Transaction transaction, TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            controller = this.controller;
            if (Bits.anyAreSet(state, REMOVED)) return;
            state = state | REMOVED;
        }
        if (controller != null) {
            controller.remove(transaction, taskFactory);
        }
    }

    void reinstall(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        synchronized (this) {
            state = state & ~REMOVED;
        }
    }

    void disableRegistry(Transaction transaction, TaskFactory taskFactory) {
        final ServiceControllerImpl<?> controller;
        synchronized (this) {
            if (Bits.allAreClear(state,  REGISTRY_ENABLED)) return;
            state = state & ~REGISTRY_ENABLED;
            controller = this.controller;
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
            controller = this.controller;
        }
        if (controller != null) {
            controller.enableRegistry(transaction, taskFactory);
        }
    }

    void installDependenciesValidateTask(final Transaction transaction, final TaskFactory taskFactory) {
        if (taskFactory == null) {
            return;
        }
        if (transaction.putAttachment(VALIDATE_TASK, Boolean.TRUE) != null) return;
        ((TaskBuilderImpl<Void>)transaction.getTaskFactory().<Void>newTask()).setValidatable(new Validatable() {
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
}
