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

import static java.lang.Thread.holdsLock;
import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;

/**
 * A service controller implementation.
 *
 * @param <S> the service type
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceControllerImpl<T> extends TransactionalObject implements ServiceController {

    // controller modes
    static final byte MODE_ACTIVE      = (byte)ServiceMode.ACTIVE.ordinal();
    static final byte MODE_LAZY        = (byte)ServiceMode.LAZY.ordinal();
    static final byte MODE_ON_DEMAND   = (byte)ServiceMode.ON_DEMAND.ordinal();
    static final byte MODE_MASK        = (byte)0b00000011;
    // controller states
    static final byte STATE_NEW        = (byte)0b00000000;
    static final byte STATE_DOWN       = (byte)0b00000100;
    static final byte STATE_STARTING   = (byte)0b00001000;
    static final byte STATE_UP         = (byte)0b00001100;
    static final byte STATE_FAILED     = (byte)0b00010000;
    static final byte STATE_STOPPING   = (byte)0b00010100;
    static final byte STATE_REMOVING   = (byte)0b00011000;
    static final byte STATE_REMOVED    = (byte)0b00011100;
    static final byte STATE_MASK       = (byte)0b00011100;
    // controller disposal flags
    static final byte SERVICE_ENABLED  = (byte)0b00100000;
    static final byte REGISTRY_ENABLED = (byte)0b01000000;
    
    /**
     * The service itself.
     */
    private final Service<T> service;
    /**
     * The primary registration of this service.
     */
    private final Registration primaryRegistration;
    /**
     * The alias registrations of this service.
     */
    private final Registration[] aliasRegistrations;
    /**
     * The dependencies of this service.
     */
    private final DependencyImpl<?>[] dependencies;
    /**
     * The service value, resulting of service start.
     */
    private T value;
    /**
     * The controller state.
     */
    private byte state = (byte)(STATE_NEW | MODE_ACTIVE);
    /**
     * The number of dependencies that are not satisfied.
     */
    private int unsatisfiedDependencies;
    /**
     * Indicates if this service is demanded to start. Has precedence over {@link downDemanded}.
     */
    private int upDemandedByCount;
    /**
     * The number of dependents that are currently running. The deployment will
     * not execute the {@code stop()} method (and subsequently leave the
     * {@link State#STOPPING} state) until all running dependents (and listeners) are stopped.
     */
    private int runningDependents;

    /**
     * Info enabled only when this service is write locked during a transaction.
     */
    // will be non null iff write locked
    private volatile TransactionalInfo transactionalInfo = null;

    /**
     * Creates the service controller, thus beginning installation.
     * 
     * @param primaryRegistration the primary registration
     * @param aliasRegistrations  the alias registrations
     * @param service             the service itself
     * @param mode                the service mode
     * @param dependencies        the service dependencies
     * @param transaction         the active transaction
     */
    ServiceControllerImpl(final Registration primaryRegistration, final Registration[] aliasRegistrations, final Service<T> service,
            final org.jboss.msc.service.ServiceMode mode, final DependencyImpl<?>[] dependencies, final Transaction transaction) {
        this.service = service;
        setMode(mode);
        this.dependencies = dependencies;
        this.aliasRegistrations = aliasRegistrations;
        this.primaryRegistration = primaryRegistration;
        lockWrite(transaction);
        unsatisfiedDependencies = dependencies.length;
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.setDependent(this, transaction);
        }
    }

    private void setMode(final ServiceMode mode) {
        if (mode != null) {
            setMode((byte)mode.ordinal());
        } else {
            // default mode (if not provided) is ACTIVE
        }
    }

    /**
     * Completes service installation, enabling the service and installing it into registrations.
     *
     * @param transaction the active transaction
     */
    void install(ServiceRegistryImpl registry, Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        // if registry is removed, get an exception right away
        registry.newServiceInstalled(this, transaction);
        if (!primaryRegistration.setController(transaction, this)) {
            throw new DuplicateServiceException("Service " + primaryRegistration.getServiceName() + " is already installed");
        }
        int installedAliases = 0; 
        for (Registration alias: aliasRegistrations) {
            // attempt to install controller at alias
            if (!alias.setController(transaction, this)) {
                // first of all, uninstall controller from installed aliases
                primaryRegistration.clearController(transaction);
                for (int j = 0; j < installedAliases; j++) {
                    aliasRegistrations[j].clearController(transaction);
                }
                throw new DuplicateServiceException("Service " + alias.getServiceName() + " is already installed");
            }
            installedAliases ++;
        }
        boolean demandDependencies;
        synchronized (this) {
            state |= SERVICE_ENABLED;
            transactionalInfo.setState(STATE_DOWN);
            demandDependencies = isMode(MODE_ACTIVE);
        }
        if (demandDependencies) {
            DemandDependenciesTask.create(this, transaction, transaction.getTaskFactory());
        }
        transactionalInfo.transition(transaction, transaction.getTaskFactory());
    }

    /**
     * Gets the primary registration.
     */
    Registration getPrimaryRegistration() {
        return primaryRegistration;
    }

    /**
     * Gets the alias registrations.
     */
    Registration[] getAliasRegistrations() {
        return aliasRegistrations;
    }

    /**
     * Gets the dependencies.
     */
    DependencyImpl<?>[] getDependencies() {
        return dependencies;
    }

    /**
     * Gets the service.
     */
    public Service<T> getService() {
        return service;
    }

    T getValue() {
        return value;
    }

    void setValue(T value) {
        this.value = value;
    }

    /**
     * Gets the current service controller state.
     */
    synchronized int getState() {
        return getState(state);
    }

    /**
     * Gets the current service controller state inside {@code transaction} context.
     * 
     * @param transaction the transaction
     */
    synchronized int getState(Transaction transaction) {
        if (lock.isOwnedBy(transaction)) {
            return transactionalInfo.getState();
        }
        return getState(state);
    }

    private static int getState(byte state) {
        return (state & STATE_MASK);
    }

    TaskController<Boolean> getStartTask(final Transaction transaction) {
        if (transactionalInfo != null && lock.isOwnedBy(transaction)) {
            return transactionalInfo.startTask;
        }
        return null;
    }

    /**
     * Management operation for disabling a service. As a result, this service will stop if it is {@code UP}.
     */
    public void disable(final Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        lockWrite(transaction);
        synchronized(this) {
            if (!isServiceEnabled()) return;
            state &= ~SERVICE_ENABLED;
            if (!isRegistryEnabled()) return;
        }
        transactionalInfo.transition(transaction, transaction.getTaskFactory());
    }

    /**
     * Management operation for enabling a service. The service may start as a result, according to its {@link
     * ServiceMode mode} rules.
     * <p> Services are enabled by default.
     */
    public void enable(final Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        lockWrite(transaction);
        synchronized(this) {
            if (isServiceEnabled()) return;
            state |= SERVICE_ENABLED;
            if (!isRegistryEnabled()) return;
        }
        transactionalInfo.transition(transaction, transaction.getTaskFactory());
    }

    private boolean isServiceEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, SERVICE_ENABLED);
    }

    void disableRegistry(Transaction transaction) {
        lockWrite(transaction);
        synchronized (this) {
            if (!isRegistryEnabled()) return;
            state &= ~REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction, transaction.getTaskFactory());
    }

    void enableRegistry(Transaction transaction) {
        lockWrite(transaction);
        synchronized (this) {
            if (isRegistryEnabled()) return;
            state |= REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction, transaction.getTaskFactory());
    }

    private boolean isRegistryEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, REGISTRY_ENABLED);
    }

    @Override
    public void retry(Transaction transaction) {
        lockWrite(transaction);
        transactionalInfo.retry(transaction);
    }

    @Override
    public void remove(Transaction transaction) {
        this.remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public void restart(Transaction transaction) {
        // TODO
    }

    /**
     * Removes this service.<p>
     * All dependent services will be automatically stopped as the result of this operation.
     * 
     * @param  transaction the active transaction
     * @param  taskFactory the task factory
     * @return the task that completes removal. Once this task is executed, the service will be at the
     *         {@code REMOVED} state.
     */
    TaskController<Void> remove(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        return transactionalInfo.scheduleRemoval(transaction, taskFactory);
    }

    /**
     * Notifies this service that it is up demanded (demanded to be UP) by one of its incoming dependencies.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void upDemanded(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        final boolean propagate;
        synchronized (this) {
            if (upDemandedByCount ++ > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            DemandDependenciesTask.create(this, transaction, taskFactory);
        }
        if (taskFactory != null) {
            transition(transaction, taskFactory);
        }
    }

    /**
     * Notifies this service that it is no longer up demanded by one of its incoming dependencies (invoked when incoming
     * dependency is being disabled or removed).
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void upUndemanded(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        final boolean propagate;
        synchronized (this) {
            if (-- upDemandedByCount > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            UndemandDependenciesTask.create(this, transaction, taskFactory);
        }
        transition(transaction, taskFactory);
    }

    /**
     * Indicates if this service is demanded to start by one or more of its incoming dependencies.
     * @return
     */
    synchronized boolean isUpDemanded() {
        return upDemandedByCount > 0;
    }

    /**
     * Notifies that a incoming dependency has started.
     * 
     * @param transaction the active transaction
     */
    void dependentStarted(Transaction transaction) {
        lockWrite(transaction);
        synchronized (this) {
            runningDependents++;
        }
    }

    /**
     * Notifies that a incoming dependency has stopped.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void dependentStopped(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        synchronized (this) {
            if (--runningDependents > 0 || taskFactory == null) {
                return;
            }
        }
        transition(transaction, taskFactory);
    }

    void dependentStopped(Transaction transaction) {
        dependentStopped(transaction, null);
    }

    public ServiceName getServiceName() {
        return primaryRegistration.getServiceName();
    }

    void dependencySatisfied(Transaction transaction, TaskFactory taskFactory, TaskController<Boolean> dependencyStartTask) {
        lockWrite(transaction);
        synchronized (ServiceControllerImpl.this) {
            -- unsatisfiedDependencies;
        }
        transactionalInfo.dependencySatisfied(transaction, taskFactory, dependencyStartTask);
    }

    public TaskController<?> dependencyUnsatisfied(Transaction transaction, TaskFactory taskFactory) {
        lockWrite(transaction);
        synchronized (this) {
           if (++ unsatisfiedDependencies > 1) {
               return null;
            }
        }
        return transition(transaction, taskFactory);
    }

    public Collection<TaskController<?>> notifyServiceDown(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        return transactionalInfo.notifyServiceDown(transaction, taskFactory);
    }

    public void notifyServiceDown(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        transactionalInfo.notifyServiceDown(transaction, null);
    }

    public void notifyServiceUp(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        transactionalInfo.notifyServiceStarting(transaction, taskFactory, null);
    }

    public void notifyServiceUp(Transaction transaction) {
        notifyServiceUp(transaction, null);
    }

    /**
     * Sets the new transactional state of this service.
     * 
     * @param transactionalState the transactional state
     * @param taskFactory        the task factory
     */
    void setTransition(byte transactionalState, Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        transactionalInfo.setTransition(transactionalState, transaction, taskFactory);
    }

    private TaskController<?> transition(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        return transactionalInfo.transition(transaction, taskFactory);
    }

    @Override
    void writeLocked() {
        transactionalInfo = new TransactionalInfo();
    }

    @Override
    void writeUnlocked() {
        state = (byte) (transactionalInfo.getState() & STATE_MASK | state & ~STATE_MASK);
        transactionalInfo = null;
    }

    @Override
    Object takeSnapshot() {
        return new Snapshot();
    }

    @SuppressWarnings("unchecked")
    @Override
    void revert(final Object snapshot) {
        if (snapshot != null) {
            ((Snapshot) snapshot).apply();
        }
    }

    final class TransactionalInfo {
        // current transactional state
        private byte transactionalState = ServiceControllerImpl.this.currentState();
        // if this service is under transition, this field points to the task that completes the transition
        private TaskController<Boolean> startTask = null;
        // contains a list of all dependencyStartTasks
        private ArrayList<TaskController<Boolean>> dependencyStartTasks = new ArrayList<TaskController<Boolean>>();

        public synchronized void dependencySatisfied(Transaction transaction, TaskFactory taskFactory, TaskController<Boolean> dependencyStartTask) {
            if (dependencyStartTask != null) {
                dependencyStartTasks.add(dependencyStartTask);
            }
            transition(transaction, taskFactory);
        }

        synchronized void setTransition(byte transactionalState, Transaction transaction, TaskFactory taskFactory) {
            this.transactionalState = transactionalState;
            if (transactionalState == STATE_REMOVED) {
                for (DependencyImpl<?> dependency: dependencies) {
                    dependency.clearDependent(transaction);
                }
            }
        }

        private synchronized void retry(Transaction transaction) {
            if (transactionalState != STATE_FAILED) {
                return;
            }
            startTask = StartingServiceTasks.create(ServiceControllerImpl.this, dependencyStartTasks, transaction, transaction.getTaskFactory());
        }

        private synchronized TaskController<?> transition(Transaction transaction, TaskFactory taskFactory) {
            TaskController<?> completeTransitionTask = null;
            assert !holdsLock(ServiceControllerImpl.this);
            switch (transactionalState) {
                case STATE_STOPPING:
                case STATE_DOWN:
                    if (unsatisfiedDependencies == 0 && shouldStart() && !isStarting(transaction)) {
                        if (StoppingServiceTasks.revertStop(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STOPPING) {
                                transactionalState = STATE_UP;
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            // a null taskFactory means we are on revert
                            throw new IllegalStateException("Cannot create task on revert");
                        }
                        transactionalState = STATE_STARTING;
                        startTask = StartingServiceTasks.create(ServiceControllerImpl.this, dependencyStartTasks, transaction, taskFactory);
                        completeTransitionTask = startTask;
                    }
                    break;
                case STATE_FAILED:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping(transaction)) {
                        transactionalState = STATE_STOPPING;
                        TaskController<Void> stopTask = StoppingServiceTasks.createForFailedService(ServiceControllerImpl.this, transaction, taskFactory);
                        completeTransitionTask = stopTask;
                    }
                    break;
                case STATE_STARTING:
                case STATE_UP:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping(transaction)) {
                        if (StartingServiceTasks.revertStart(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STARTING) {
                                transactionalState = STATE_DOWN;
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            // a null taskFactory means we are on revert
                            throw new IllegalStateException("Cannot create task on revert");
                        }
                        final Collection<TaskController<?>> dependentTasks = notifyServiceDown(transaction, taskFactory);
                        transactionalState = STATE_STOPPING;
                        TaskController<Void> stopTask = StoppingServiceTasks.create(ServiceControllerImpl.this, dependentTasks, transaction, taskFactory);
                        completeTransitionTask = stopTask;
                    }
                    break;
                default:
                    break;

            }
            return completeTransitionTask;
        }

       private void notifyServiceStarting(Transaction transaction, TaskFactory taskFactory, TaskController<Boolean> startTask) {
            primaryRegistration.serviceUp(transaction, taskFactory, startTask);
            for (Registration registration: aliasRegistrations) {
                registration.serviceUp(transaction, taskFactory, startTask);
            }
        }

        private Collection<TaskController<?>> notifyServiceDown(Transaction transaction, TaskFactory taskFactory) {
            final List<TaskController<?>> tasks = new ArrayList<TaskController<?>>();
            primaryRegistration.serviceDown(transaction, taskFactory, tasks);
            for (Registration registration: aliasRegistrations) {
                registration.serviceDown(transaction, taskFactory, tasks);
            }
            return tasks;
        }

        private synchronized TaskController<Void> scheduleRemoval(Transaction transaction, TaskFactory taskFactory) {
            // idempotent
            if (getState() == STATE_REMOVED) {
                return null;
            }
            // disable service
            synchronized (ServiceControllerImpl.this) {
                state &= ~SERVICE_ENABLED;
            }
            // transition disabled service, guaranteeing that it is either at DOWN state or it will get to this state
            // after complete transition task completes
            final TaskController<?> stoppingTask = transition(transaction, taskFactory);
            assert getState() == STATE_DOWN || isStopping(transaction);// prevent hard to find bugs
            // create remove task
            final TaskBuilder<Void> removeTaskBuilder = taskFactory.newTask(new ServiceRemoveTask(ServiceControllerImpl.this, transaction));
            if (stoppingTask != null) {
                removeTaskBuilder.addDependency(stoppingTask);
            }
            final TaskController<Void> removeTask = removeTaskBuilder.release();
            return removeTask;
        }

        private void setState(final byte sid) {
            transactionalState = sid;
        }

        private byte getState() {
            return transactionalState;
        }

        private boolean isStarting(Transaction transaction) {
            return transactionalState == STATE_STARTING;
        }

        private boolean isStopping(Transaction transaction) {
            return transactionalState == STATE_STOPPING;
        }
    }

    private final class Snapshot {
        private final byte state;
        private final int upDemandedByCount;
        private final int unsatisfiedDependencies;
        private final int runningDependents;

        // take snapshot
        public Snapshot() {
            assert holdsLock(ServiceControllerImpl.this);
            state = ServiceControllerImpl.this.state;
            upDemandedByCount = ServiceControllerImpl.this.upDemandedByCount;
            unsatisfiedDependencies = ServiceControllerImpl.this.unsatisfiedDependencies;
            runningDependents = ServiceControllerImpl.this.runningDependents;
        }

        // revert ServiceController state to what it was when snapshot was taken; invoked on rollback
        public void apply() {
            assert holdsLock(ServiceControllerImpl.this);
            ServiceControllerImpl.this.state = state;
            ServiceControllerImpl.this.upDemandedByCount = upDemandedByCount;
            ServiceControllerImpl.this.unsatisfiedDependencies = unsatisfiedDependencies;
            ServiceControllerImpl.this.runningDependents = runningDependents;
        }
    }

    private synchronized boolean shouldStart() {
        return (isMode(MODE_ACTIVE) || upDemandedByCount > 0) && Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED);
    }

    private synchronized boolean shouldStop() {
        return (isMode(MODE_ON_DEMAND) && upDemandedByCount == 0) || !Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED);
    }

    private void setMode(final byte mid) {
        state = (byte) (mid & MODE_MASK | state & ~MODE_MASK);
    }

    private boolean isMode(final byte mode) {
        assert holdsLock(this);
        return (state & MODE_MASK) == mode;
    }

    private byte currentState() {
        assert holdsLock(this);
        return (byte)(state & STATE_MASK);
    }
}