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
import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc.txn.Helper.validateTransaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;

/**
 * A service controller implementation.
 *
 * @param <T> the service type
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceControllerImpl<T> extends ServiceManager implements ServiceController {

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
    static final byte STATE_RESTARTING = (byte)0b00011000;
    static final byte STATE_REMOVING   = (byte)0b00011100;
    static final byte STATE_REMOVED    = (byte)0b00100000;
    static final byte STATE_MASK       = (byte)0b00111100;
    // controller disposal flags
    static final byte SERVICE_ENABLED  = (byte)0b01000000;
    static final byte REGISTRY_ENABLED = (byte)0b10000000;

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
    final DependencyImpl<?>[] dependencies;
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
     * Indicates if this service is demanded to start.
     */
    private int demandedByCount;

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
        this.primaryRegistration = primaryRegistration;
        this.aliasRegistrations = aliasRegistrations;
        this.dependencies = dependencies;
        initTransactionalInfo(transaction);
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
    boolean install(Transaction transaction, TaskFactory taskFactory) {
        primaryRegistration.installService(transaction);
        for (Registration alias: aliasRegistrations) {
            alias.installService(transaction);
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

    void reinstall(final Transaction transaction) {
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.setDependent(this, transaction);
        }
        install();
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
     * Gets the current service controller state inside {@code transaction} context.
     */
    synchronized int getState() {
        return transactionalInfo != null ? transactionalInfo.getState() : getState(state);
    }

    private static int getState(byte state) {
        return (state & STATE_MASK);
    }

    synchronized TaskController<?> getStartTask(final Transaction transaction) {
        return transactionalInfo != null ? transactionalInfo.startTask : null;
    }

    @Override
    public void disable(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        super.disable(transaction);
    }

    @Override
    public boolean doDisable(final Transaction transaction, final TaskFactory taskFactory) {
        initTransactionalInfo(transaction);
        synchronized(this) {
            if (!isServiceEnabled()) return false;
            state &= ~SERVICE_ENABLED;
            if (!isRegistryEnabled()) return true;
        }
        transactionalInfo.transition(transaction, taskFactory);
        return true;
    }

    @Override
    public void enable(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        super.enable(transaction);
    }

    @Override
    public boolean doEnable(final Transaction transaction, final TaskFactory taskFactory) {
        initTransactionalInfo(transaction);
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
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (!isRegistryEnabled()) return;
            state &= ~REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction, taskFactory);
    }

    void enableRegistry(final Transaction transaction, final TaskFactory taskFactory) {
        initTransactionalInfo(transaction);
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
    public void retry(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        initTransactionalInfo(transaction);
        transactionalInfo.retry(transaction);
    }
    
    @Override
    public void remove(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public void restart(Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        initTransactionalInfo(transaction);
        transactionalInfo.restart(transaction);
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
        synchronized (this) {
            if (getState(state) == STATE_REMOVED) {
                return null;
            }
        }
        initTransactionalInfo(transaction);
        return transactionalInfo.scheduleRemoval(transaction, taskFactory);
    }

    /**
     * Notifies this service that it is demanded by one of its incoming dependencies.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void demand(final Transaction transaction, final TaskFactory taskFactory) {
        initTransactionalInfo(transaction);
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
        transactionalInfo.transition(transaction, taskFactory);
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
        initTransactionalInfo(transaction);
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
        transactionalInfo.transition(transaction, taskFactory);
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

    public ServiceName getServiceName() {
        return primaryRegistration.getServiceName();
    }

    void dependencySatisfied(final Transaction transaction, final TaskFactory taskFactory, final TaskController<?> dependencyStartTask) {
        initTransactionalInfo(transaction);
        synchronized (ServiceControllerImpl.this) {
            -- unsatisfiedDependencies;
        }
        transactionalInfo.dependencySatisfied(transaction, taskFactory, dependencyStartTask);
    }

    public TaskController<?> dependencyUnsatisfied(final Transaction transaction, final TaskFactory taskFactory) {
        initTransactionalInfo(transaction);
        synchronized (this) {
           if (++ unsatisfiedDependencies > 1) {
               return null;
            }
        }
        return transactionalInfo.transition(transaction, taskFactory);
    }

    /* Transition related methods */

    void setServiceUp(T result, Transaction transaction, TaskFactory taskFactory) {
        setValue(result);
        transactionalInfo.setTransition(STATE_UP, transaction, taskFactory);
    }

    void setServiceFailed(Transaction transaction, TaskFactory taskFactory) {
        MSCLogger.FAIL.startFailed(getServiceName());
        transactionalInfo.setTransition(STATE_FAILED, transaction, taskFactory);
    }

    void setServiceDown(Transaction transaction, TaskFactory taskFactory) {
        setValue(null);
        transactionalInfo.setTransition(STATE_DOWN, transaction, taskFactory);
    }

    void setServiceRemoved(Transaction transaction, TaskFactory taskFactory) {
        clear(transaction, taskFactory);
        transactionalInfo.setTransition(STATE_REMOVED, transaction, taskFactory);
    }

    void notifyServiceStarting(Transaction transaction, TaskFactory taskFactory, TaskController<?> startTask) {
        primaryRegistration.serviceStarting(transaction, taskFactory, startTask);
        for (Registration registration: aliasRegistrations) {
            registration.serviceStarting(transaction, taskFactory, startTask);
        }
    }

    void notifyServiceUp(Transaction transaction) {
        notifyServiceStarting(transaction, null, null);
    }

    Collection<TaskController<?>> notifyServiceFailed(Transaction transaction, TaskFactory taskFactory) {
        final List<TaskController<?>> tasks = new ArrayList<>();
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
        final List<TaskController<?>> tasks = new ArrayList<>();
        primaryRegistration.serviceStopping(transaction, taskFactory, tasks);
        for (Registration registration: aliasRegistrations) {
            registration.serviceStopping(transaction, taskFactory, tasks);
        }
        return tasks;
    }

    boolean revertStopping(Transaction transaction, TaskFactory taskFactory) {
        if (transactionalInfo.getTransition() == STATE_STOPPING) {
            transactionalInfo.setTransition(STATE_UP, transaction, taskFactory);
            return true;
        }
        return false;
    }

    boolean revertStarting(Transaction transaction, TaskFactory taskFactory) {
        if (transactionalInfo.getTransition() == STATE_STARTING) {
            setServiceDown(transaction, taskFactory);
            return true;
        }
        return false;
    }

    private synchronized void initTransactionalInfo(final Transaction transaction) {
        if (transactionalInfo == null) {
            transactionalInfo = new TransactionalInfo();
            transaction.addListener(new TerminateCompletionListener() {
                @Override
                public void transactionTerminated() {
                    clean();
                }
            });
        }
    }

    private synchronized  void clean() {
        // prevent hard to find bugs
        assert transactionalInfo.getState() == STATE_NEW || transactionalInfo.getState() == STATE_UP || transactionalInfo.getState() == STATE_DOWN || transactionalInfo.getState() == STATE_FAILED || transactionalInfo.getState() == STATE_REMOVED: "State: " + transactionalInfo.getState();
        state = (byte) (transactionalInfo.getState() | state & ~STATE_MASK);
        transactionalInfo = null;
    }

    final class TransactionalInfo {
        // current transactional state - must be always set via {@link #setState()} method, not directly
        private byte transactionalState = ServiceControllerImpl.this.currentState();
        // if this service is under transition, this field points to the task that completes the transition
        private TaskController<T> startTask = null;
        // contains a list of all dependencyStartTasks
        private ArrayList<TaskController<?>> dependencyStartTasks = new ArrayList<>();

        public synchronized void dependencySatisfied(Transaction transaction, TaskFactory taskFactory, TaskController<?> dependencyStartTask) {
            if (dependencyStartTask != null) {
                dependencyStartTasks.add(dependencyStartTask);
            }
            transition(transaction, taskFactory);
        }

        byte getTransition() {
            return transactionalState;
        }

        void setTransition(byte transactionalState, Transaction transaction, TaskFactory taskFactory) {
            // time to restart the service
            if (transactionalState == STATE_RESTARTING) {
                if (transactionalState == STATE_UP || transactionalState == STATE_FAILED) {
                    return;
                } else {
                    assert transactionalState == STATE_DOWN;
                    transactionalState = STATE_DOWN;
                    transition(transaction, taskFactory);
                }
            }
            setState(transactionalState);
        }

        private synchronized void retry(Transaction transaction) {
            if (transactionalState != STATE_FAILED) {
                return;
            }
            startTask = StartServiceTask.create(ServiceControllerImpl.this, dependencyStartTasks, transaction, transaction.getTaskFactory());
        }

        private synchronized TaskController<?> transition(Transaction transaction, TaskFactory taskFactory) {
            TaskController<?> completeTransitionTask = null;
            assert !holdsLock(ServiceControllerImpl.this);
            switch (transactionalState) {
                case STATE_STOPPING:
                case STATE_DOWN:
                    if (unsatisfiedDependencies == 0 && shouldStart() && !isStarting()) {
                        if (StopServiceTask.revert(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STOPPING) {
                                setState(STATE_UP);
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            taskFactory = transaction.getTaskFactory();
                        }
                        setState(STATE_STARTING);
                        startTask = StartServiceTask.create(ServiceControllerImpl.this, dependencyStartTasks, transaction, taskFactory);
                        completeTransitionTask = startTask;
                    }
                    break;
                case STATE_FAILED:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping()) {
                        setState(STATE_STOPPING);
                        completeTransitionTask = StopFailedServiceTask.create(ServiceControllerImpl.this, transaction, taskFactory);
                    }
                    break;
                case STATE_STARTING:
                case STATE_UP:
                    if ((unsatisfiedDependencies > 0 || shouldStop()) && !isStopping()) {
                        if (StartServiceTask.revert(ServiceControllerImpl.this, transaction)) {
                            if (transactionalState == STATE_STARTING) {
                                setState(STATE_DOWN);
                            }
                            break;
                        }
                        if (taskFactory == null) {
                            return null;
                        }
                        final Collection<TaskController<?>> dependentTasks = notifyServiceDown(transaction, taskFactory);
                        setState(STATE_STOPPING);
                        completeTransitionTask = StopServiceTask.create(ServiceControllerImpl.this, dependentTasks, transaction, taskFactory);
                    }
                    break;
                default:
                    break;

            }
            return completeTransitionTask;
        }

        private synchronized void restart(Transaction transaction) {
            if (state == STATE_UP || state == STATE_STARTING) {
                final Collection<TaskController<?>> dependentTasks = notifyServiceDown(transaction, transaction.getTaskFactory());
                setState(STATE_RESTARTING);
                StopServiceTask.create(ServiceControllerImpl.this, startTask, dependentTasks, transaction, transaction.getTaskFactory());
            }
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
            final TaskController<?> stopTask = transition(transaction, taskFactory);
            assert isStopping() || transactionalState == STATE_DOWN || transactionalState == STATE_NEW; // prevent hard to find bugs
            return RemoveServiceTask.create(ServiceControllerImpl.this, stopTask, transaction, taskFactory);
        }

        private void setState(final byte sid) {
            assert transactionalState != sid;
            if (dependencies.length == 0) {
                transactionalState = sid;
            } else {
                // this controller has dependencies - we need to deal with 'cycle detection set' transitions
                final boolean leavingDownState = transactionalState == STATE_DOWN && sid != STATE_REMOVED;
                final boolean enteringDownState = transactionalState != STATE_NEW && transactionalState != STATE_REMOVED && sid == STATE_DOWN;
                transactionalState = sid;
                if (leavingDownState) {
                    leaveCycleDetectionSet();
                } else if (enteringDownState) {
                    enterCycleDetectionSet();
                }
            }
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

    private void leaveCycleDetectionSet() {
        assert dependencies.length != 0;
        final ControllerHolder newValue = new ControllerHolder(false, this);
        replaceHolder(primaryRegistration, newValue);
        for (final Registration aliasRegistration : aliasRegistrations) {
            replaceHolder(aliasRegistration, newValue);
        }
    }

    private void enterCycleDetectionSet() {
        assert dependencies.length != 0;
        final ControllerHolder newValue = new ControllerHolder(true, this);
        replaceHolder(primaryRegistration, newValue);
        for (final Registration aliasRegistration : aliasRegistrations) {
            replaceHolder(aliasRegistration, newValue);
        }
    }

    private void replaceHolder(final Registration registration, final ControllerHolder newValue) {
        boolean success = false;
        while (!success) {
            final ControllerHolder oldValue = registration.holderRef.get();
            if (oldValue == null) return; // controller was removed - we're finished for now
            assert oldValue.inCycleDetectionSet != newValue.inCycleDetectionSet;
            success = registration.holderRef.compareAndSet(oldValue, newValue);
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

    void install() throws DuplicateServiceException, CircularDependencyException {
        final ControllerHolder controllerHolder = new ControllerHolder(dependencies.length != 0, this);

        // associate controller holder with primary registration
        if (!primaryRegistration.holderRef.compareAndSet(null, controllerHolder)) {
            throw SERVICE.duplicateService(primaryRegistration.getServiceName());
        }
        boolean ok = false;
        int lastIndex = -1;
        try {
            // associate controller holder with alias registrations
            for (int i = 0; i < aliasRegistrations.length; lastIndex = i++) {
                if (!aliasRegistrations[i].holderRef.compareAndSet(null, controllerHolder)) {
                    throw SERVICE.duplicateService(aliasRegistrations[i].getServiceName());
                }
            }
            CycleDetector.execute(this);
            ok = true;
        } finally {
            if (!ok) {
                // exception was thrown, cleanup
                for (int i = 0; i < lastIndex; i++) {
                    aliasRegistrations[i].holderRef.set(null);
                }
                primaryRegistration.holderRef.set(null);
            }
        }
    }

}
