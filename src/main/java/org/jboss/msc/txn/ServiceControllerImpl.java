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

import org.jboss.msc.service.ServiceController;
import org.jboss.msc._private.MSCLogger;
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
final class ServiceControllerImpl<T> extends ServiceManager implements ServiceController, TransactionalLock.Cleaner {

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
    private final Object service;
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
    private int demandedByCount;
    /**
     * The number of dependents that are currently running. The deployment will
     * not execute the {@code stop()} method (and subsequently leave the
     * {@link State#STOPPING} state) until all running dependents (and listeners) are stopped.
     */
    private int runningDependents;
    /**
     * Transactional lock.
     */
    private TransactionalLock lock = new TransactionalLock(this);

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
    ServiceControllerImpl(final Registration primaryRegistration, final Registration[] aliasRegistrations, final Object service,
            final org.jboss.msc.service.ServiceMode mode, final DependencyImpl<?>[] dependencies, final Transaction transaction) {
        this.service = service;
        setMode(mode);
        this.dependencies = dependencies;
        this.aliasRegistrations = aliasRegistrations;
        this.primaryRegistration = primaryRegistration;
        lock.tryLock(transaction);
        assert lock.isOwnedBy(transaction);
        initTransactionalInfo();
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
    boolean install(Transaction transaction, TaskFactory taskFactory, ReportableContext reportableContext) {
        assert lock.isOwnedBy(transaction);
        primaryRegistration.installService(this, transaction);
        for (Registration alias: aliasRegistrations) {
            alias.installService(this, transaction);
        }
        boolean demandDependencies;
        synchronized (this) {
            state |= SERVICE_ENABLED;
            transactionalInfo.setState(STATE_DOWN);
            demandDependencies = isMode(MODE_ACTIVE);
        }
        if (demandDependencies) {
            demandDependencies(transaction, taskFactory);
        }
        transactionalInfo.transition(transaction, taskFactory);
        return true;
    }

    void reinstall(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.setDependent(this, transaction);
        }
        primaryRegistration.reinstallService(this, transaction);
        for (Registration alias: aliasRegistrations) {
            alias.reinstallService(this, transaction);
        }
        boolean demandDependencies;
        synchronized (this) {
            state |= SERVICE_ENABLED;
            transactionalInfo.setState(STATE_DOWN);
            demandDependencies = isMode(MODE_ACTIVE);
        }
        if (demandDependencies) {
            demandDependencies(transaction, null);
        }
    }

    void clear(Transaction transaction, TaskFactory taskFactory) {
        primaryRegistration.clearController(transaction, taskFactory);
        for (Registration registration: aliasRegistrations) {
            registration.clearController(transaction, taskFactory);
        }
        final boolean undemand = isMode(MODE_ACTIVE);
        for (DependencyImpl<?> dependency: dependencies) {
            if (undemand) {
                dependency.undemand(transaction, taskFactory);
            }
            dependency.clearDependent(transaction, taskFactory);
        }
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
    public Object getService() {
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

     TaskController<?> getStartTask(final Transaction transaction) {
        if (transactionalInfo != null && lock.isOwnedBy(transaction)) {
            return transactionalInfo.startTask;
        }
        return null;
    }

    @Override
    public boolean doDisable(final Transaction transaction, final TaskFactory taskFactory) {
        lock.lockSynchronously(transaction);
        initTransactionalInfo();
        synchronized(this) {
            if (!isServiceEnabled()) return false;
            state &= ~SERVICE_ENABLED;
            if (!isRegistryEnabled()) return true;
        }
        transactionalInfo.transition(transaction, taskFactory);
        return true;
    }

    @Override
    public boolean doEnable(final Transaction transaction, final TaskFactory taskFactory) {
        lock.lockSynchronously(transaction);
        initTransactionalInfo();
        synchronized(this) {
            if (isServiceEnabled()) return false;
            state |= SERVICE_ENABLED;
            if (!isRegistryEnabled()) return true;
        }
        transactionalInfo.transition(transaction, taskFactory);
        return true;
    }

    private boolean isServiceEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, SERVICE_ENABLED);
    }

    void disableRegistry(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doDisableRegistry(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doDisableRegistry(transaction, taskFactory);
                }
            });
        }
        
    }

    private void doDisableRegistry(Transaction transaction, TaskFactory taskFactory) {
        initTransactionalInfo();
        synchronized (this) {
            if (!isRegistryEnabled()) return;
            state &= ~REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction, taskFactory);
    }

    void enableRegistry(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doEnableRegistry(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doEnableRegistry(transaction, taskFactory);
                }
            });
        }
    }

    void doEnableRegistry(Transaction transaction, TaskFactory taskFactory) {
        initTransactionalInfo();
        synchronized (this) {
            if (isRegistryEnabled()) return;
            state |= REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction, taskFactory);
    }

    private boolean isRegistryEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, REGISTRY_ENABLED);
    }

    @Override
    public void retry(final Transaction transaction) {
        if (lock.tryLock(transaction)) {
            doRetry(transaction);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doRetry(transaction);
                }
            });
        }
    }
    
    private void doRetry(Transaction transaction) {
        initTransactionalInfo();
        transactionalInfo.retry(transaction);
    }

    @Override
    public void remove(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        transaction.ensureIsActive();
        this.remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public void restart(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        transaction.ensureIsActive();
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
        // idempotent
        if (getState() == STATE_REMOVED) {
            return null;
        }
        lock.lockSynchronously(transaction);
        initTransactionalInfo();
        return transactionalInfo.scheduleRemoval(transaction, taskFactory);
    }

    /**
     * Notifies this service that it is demanded by one of its incoming dependencies.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void demand(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doDemand(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doDemand(transaction, taskFactory);
                }
            });
        }
    }

    void doDemand(Transaction transaction, TaskFactory taskFactory) {
        initTransactionalInfo();
        final boolean propagate;
        synchronized (this) {
            if (demandedByCount ++ > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            demandDependencies(transaction, taskFactory);
        }
        transition(transaction, taskFactory);
    }

    /**
     * Demands this service's dependencies to start.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    private void demandDependencies(Transaction transaction, TaskFactory taskFactory) {
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.demand(transaction, taskFactory);
        }
    }

    /**
     * Notifies this service that it is no longer demanded by one of its incoming dependencies (invoked when incoming
     * dependency is being disabled or removed).
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void undemand(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doUndemand(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doUndemand(transaction, taskFactory);
                }
            });
        }
    }

    private void doUndemand(Transaction transaction, TaskFactory taskFactory) {
        initTransactionalInfo();
        final boolean propagate;
        synchronized (this) {
            if (-- demandedByCount > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            undemandDependencies(transaction, taskFactory);
        }
        transition(transaction, taskFactory);
    }

    /**
     * Undemands this service's dependencies to start.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    private void undemandDependencies(Transaction transaction, TaskFactory taskFactory) {
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.undemand(transaction, taskFactory);
        }
    }

    /**
     * Indicates if this service is demanded to start by one or more of its incoming dependencies.
     * @return
     */
    synchronized boolean isUpDemanded() {
        return demandedByCount > 0;
    }

    /**
     * Notifies that a incoming dependency has started.
     * 
     * @param transaction the active transaction
     */
    void dependentStarted(Transaction transaction) {
        if (lock.tryLock(transaction)) {
            doDependentStarted();
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doDependentStarted();
                }
            });
        }
    }

    private synchronized void doDependentStarted() {
        initTransactionalInfo();
        runningDependents++;
    }

    /**
     * Notifies that a incoming dependency has stopped.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void dependentStopped(final Transaction transaction, final TaskFactory taskFactory) {
        if (lock.tryLock(transaction)) {
            doDependentStopped(transaction, taskFactory);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doDependentStopped(transaction, taskFactory);
                }
            });
        }
    }

    void doDependentStopped(Transaction transaction, TaskFactory taskFactory) {
        initTransactionalInfo();
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

    void dependencySatisfied(final Transaction transaction, final TaskFactory taskFactory, final TaskController<?> dependencyStartTask) {
        if (lock.tryLock(transaction)) {
            doDependencySatisfied(transaction, taskFactory, dependencyStartTask);
        } else {
            lock.lockAsynchronously(transaction, new LockListener() {

                public void lockAcquired() {
                    doDependencySatisfied(transaction, taskFactory, dependencyStartTask);
                }
            });
        }
    }

    private void doDependencySatisfied(Transaction transaction, TaskFactory taskFactory, TaskController<?> dependencyStartTask) {
        initTransactionalInfo();
        synchronized (ServiceControllerImpl.this) {
            -- unsatisfiedDependencies;
        }
        transactionalInfo.dependencySatisfied(transaction, taskFactory, dependencyStartTask);
    }

    public TaskController<?> dependencyUnsatisfied(final Transaction transaction, final TaskFactory taskFactory) {
        lock.lockSynchronously(transaction);
        initTransactionalInfo();
        synchronized (this) {
           if (++ unsatisfiedDependencies > 1) {
               return null;
            }
        }
        return transition(transaction, taskFactory);
    }

    /* Transition related methods */

    void setServiceUp(T result, Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        setValue(result);
        transactionalInfo.setTransition(ServiceControllerImpl.STATE_UP, transaction);
    }

    void setServiceUp(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        if (transactionalInfo.getTransition(transaction) == ServiceControllerImpl.STATE_FAILED) {
            return;
        }
        transactionalInfo.setTransition(ServiceControllerImpl.STATE_UP, transaction);
    }

    void setServiceFailed(Transaction transaction) {
        MSCLogger.FAIL.startFailed(getServiceName());
        transactionalInfo.setTransition(ServiceControllerImpl.STATE_FAILED, transaction);
    }

    void setServiceDown(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        setValue(null);
        transactionalInfo.setTransition(ServiceControllerImpl.STATE_DOWN, transaction);
    }

    void setServiceRemoved(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        clear(transaction, taskFactory);
        transactionalInfo.setTransition(ServiceControllerImpl.STATE_REMOVED, transaction);
    }

    void notifyServiceStarting(Transaction transaction, TaskFactory taskFactory, TaskController<?> startTask) {
        assert lock.isOwnedBy(transaction);
        primaryRegistration.serviceStarting(transaction, taskFactory, startTask);
        for (Registration registration: aliasRegistrations) {
            registration.serviceStarting(transaction, taskFactory, startTask);
        }
    }

    void notifyServiceUp(Transaction transaction) {
        notifyServiceStarting(transaction, null, null);
    }

    Collection<TaskController<?>> notifyServiceFailed(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        final List<TaskController<?>> tasks = new ArrayList<TaskController<?>>();
        primaryRegistration.serviceFailed(transaction, taskFactory, tasks);
        for (Registration registration: aliasRegistrations) {
            registration.serviceFailed(transaction, taskFactory, tasks);
        }
        return tasks;
    }

    void notifyServiceDown(Transaction transaction) {
        notifyServiceDown(transaction, null);
    }

    Collection<TaskController<?>> notifyServiceDown(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        final List<TaskController<?>> tasks = new ArrayList<TaskController<?>>();
        primaryRegistration.serviceStopping(transaction, taskFactory, tasks);
        for (Registration registration: aliasRegistrations) {
            registration.serviceStopping(transaction, taskFactory, tasks);
        }
        return tasks;
    }

    boolean revertStopping(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        if (transactionalInfo.getTransition(transaction) == ServiceControllerImpl.STATE_STOPPING) {
            transactionalInfo.setTransition(ServiceControllerImpl.STATE_UP, transaction);
            return true;
        }
        return false;
    }

    boolean revertStarting(Transaction transaction) {
        assert lock.isOwnedBy(transaction);
        if (transactionalInfo.getTransition(transaction) == ServiceControllerImpl.STATE_STARTING) {
            setServiceDown(transaction);
            return true;
        }
        return false;
    }

    private TaskController<?> transition(Transaction transaction, TaskFactory taskFactory) {
        assert lock.isOwnedBy(transaction);
        return transactionalInfo.transition(transaction, taskFactory);
    }

    private synchronized void initTransactionalInfo() {
        if (transactionalInfo == null) {
            transactionalInfo = new TransactionalInfo();
        }
    }

    @Override
    public synchronized  void clean() {
        // prevent hard to find bugs
        assert transactionalInfo.getState() == STATE_UP || transactionalInfo.getState() == STATE_DOWN || transactionalInfo.getState() == STATE_FAILED || transactionalInfo.getState() == STATE_REMOVED: "State: " + transactionalInfo.getState();
        state = (byte) (transactionalInfo.getState() | state & ~STATE_MASK);
        transactionalInfo = null;
    }

    final class TransactionalInfo {
        // current transactional state
        private byte transactionalState = ServiceControllerImpl.this.currentState();
        // if this service is under transition, this field points to the task that completes the transition
        private TaskController<T> startTask = null;
        // contains a list of all dependencyStartTasks
        private ArrayList<TaskController<?>> dependencyStartTasks = new ArrayList<TaskController<?>>();

        public synchronized void dependencySatisfied(Transaction transaction, TaskFactory taskFactory, TaskController<?> dependencyStartTask) {
            if (dependencyStartTask != null) {
                dependencyStartTasks.add(dependencyStartTask);
            }
            transition(transaction, taskFactory);
        }

        byte getTransition(Transaction transaction) {
            assert lock.isOwnedBy(transaction);
            return transactionalState;
        }

        void setTransition(byte transactionalState, Transaction transaction) {
            assert lock.isOwnedBy(transaction);
            this.transactionalState = transactionalState;
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
                    if (unsatisfiedDependencies == 0 && shouldStart() && !isStarting()) {
                        if (StoppingServiceTasks.revertStop(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STOPPING) {
                                transactionalState = STATE_UP;
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            taskFactory = transaction.getTaskFactory();
                        }
                        transactionalState = STATE_STARTING;
                        startTask = StartingServiceTasks.create(ServiceControllerImpl.this, dependencyStartTasks, transaction, taskFactory);
                        completeTransitionTask = startTask;
                    }
                    break;
                case STATE_FAILED:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping()) {
                        transactionalState = STATE_STOPPING;
                        TaskController<Void> stopTask = StoppingServiceTasks.createForFailedService(ServiceControllerImpl.this, transaction, taskFactory);
                        completeTransitionTask = stopTask;
                    }
                    break;
                case STATE_STARTING:
                case STATE_UP:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping()) {
                        if (StartingServiceTasks.revertStart(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STARTING) {
                                transactionalState = STATE_DOWN;
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            return null;
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

        private synchronized TaskController<Void> scheduleRemoval(Transaction transaction, TaskFactory taskFactory) {
            // idempotent
            if (transactionalState == STATE_REMOVED) {
                return null;
            }
            // disable service
            synchronized (ServiceControllerImpl.this) {
                state &= ~SERVICE_ENABLED;
            }
            // transition disabled service, guaranteeing that it is either at DOWN state or it will get to this state
            // after complete transition task completes
            final TaskController<?> stoppingTask = transition(transaction, taskFactory);
            assert isStopping() || transactionalState == STATE_DOWN;// prevent hard to find bugs
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

        private boolean isStarting() {
            return transactionalState == STATE_STARTING;
        }

        private boolean isStopping() {
            return transactionalState == STATE_STOPPING;
        }
    }

    private synchronized boolean shouldStart() {
        return (isMode(MODE_ACTIVE) || demandedByCount > 0) && Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED);
    }

    private synchronized boolean shouldStop() {
        return (isMode(MODE_ON_DEMAND) && demandedByCount == 0) || !Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED);
    }

    private void setMode(final byte mid) {
        state = (byte) (mid & MODE_MASK | state & ~MODE_MASK);
    }

    private boolean isMode(final byte mode) {
        return (state & MODE_MASK) == mode;
    }

    private byte currentState() {
        assert holdsLock(this);
        return (byte)(state & STATE_MASK);
    }
}
