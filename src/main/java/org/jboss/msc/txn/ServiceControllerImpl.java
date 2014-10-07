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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;

import static java.lang.Thread.holdsLock;
import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc.txn.Helper.getAbstractTransaction;
import static org.jboss.msc.txn.Helper.setModified;
import static org.jboss.msc.txn.Helper.validateTransaction;

/**
 * A service controller implementation.
 *
 * @param <T> the service type
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceControllerImpl<T> implements ServiceController {

    // controller modes
    static final byte MODE_ACTIVE      = (byte)ServiceMode.ACTIVE.ordinal();
    static final byte MODE_LAZY        = (byte)ServiceMode.LAZY.ordinal();
    static final byte MODE_ON_DEMAND   = (byte)ServiceMode.ON_DEMAND.ordinal();
    static final byte MODE_MASK        = (byte)0b00000011;
    // controller states
    static final byte STATE_DOWN       = (byte)0b00000100;
    static final byte STATE_STARTING   = (byte)0b00001000;
    static final byte STATE_UP         = (byte)0b00001100;
    static final byte STATE_FAILED     = (byte)0b00010000;
    static final byte STATE_STOPPING   = (byte)0b00010100;
    static final byte STATE_RESTARTING = (byte)0b00011000;
    static final byte STATE_REMOVED    = (byte)0b00011100;
    static final byte STATE_MASK       = (byte)0b00011100;
    // controller flags
    static final byte SERVICE_ENABLED  = (byte)0b00100000;
    static final byte SERVICE_REMOVED  = (byte)0b01000000;
    static final byte REGISTRY_ENABLED = (byte)0b10000000;

    // TODO do we allow null values for non-void services?
    private static final Object NULL_VALUE = new Object(); 

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
    private volatile byte state = (byte)(STATE_DOWN | MODE_ACTIVE);
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
        unsatisfiedDependencies = dependencies.length;
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.setDependent(this, transaction); // TODO: this escapes contructor!!!
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
     * Begins services installation, by bounding service with its registrations (primary and aliases) and dependencies.
     * 
     * @throws DuplicateServiceException   if there is already a service installed at any of the registrations
     * @throws CircularDependencyException if installation of this services creates a dependency cycle
     */
    void beginInstallation() throws DuplicateServiceException, CircularDependencyException {
        // associate controller holder with primary registration
        if (!primaryRegistration.holderRef.compareAndSet(null, this)) {
            throw SERVICE.duplicateService(primaryRegistration.getServiceName());
        }
        boolean ok = false;
        int lastIndex = -1;
        try {
            // associate controller holder with alias registrations
            for (int i = 0; i < aliasRegistrations.length; lastIndex = i++) {
                if (!aliasRegistrations[i].holderRef.compareAndSet(null, this)) {
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

    /**
     * Completes service installation, enabling the service and installing it into registrations.
     *
     * @param transaction the active transaction
     */
    void completeInstallation(final Transaction transaction) {
        primaryRegistration.installService(transaction);
        for (Registration alias: aliasRegistrations) {
            alias.installService(transaction);
        }
        boolean demandDependencies;
        synchronized (this) {
            state |= SERVICE_ENABLED;
            demandDependencies = isMode(MODE_ACTIVE);
        }
        if (demandDependencies) {
            demandDependencies(transaction);
        }
        transactionalInfo.transition(transaction);
    }

    void clear(Transaction transaction) {
        primaryRegistration.clearController(transaction);
        for (Registration registration: aliasRegistrations) {
            registration.clearController(transaction);
        }
        final boolean undemand = isMode(MODE_ACTIVE);
        for (DependencyImpl<?> dependency: dependencies) {
            if (undemand) {
                dependency.undemand(transaction);
            }
            dependency.clearDependent(transaction);
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
        if (value == null &&
                ((transactionalInfo != null && transactionalInfo.getState() != STATE_UP) || getState() != STATE_UP)) {
            throw MSCLogger.SERVICE.serviceNotStarted(primaryRegistration.getServiceName());
        }
        return value == NULL_VALUE? null: value;
    }

    @SuppressWarnings("unchecked") // really ugly, but do we have a better solution?
    void setValue(T value) {
        this.value = value == null? (T) NULL_VALUE: value;
    }

    /**
     * Gets the current service controller state inside {@code transaction} context.
     */
    int getState() {
        return transactionalInfo != null ? transactionalInfo.getState() : getState(state);
    }

    private static int getState(byte state) {
        return (state & STATE_MASK);
    }

    @Override
    public void disable(final UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        setModified(transaction);
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
            if (!isServiceEnabled()) return;
            state &= ~SERVICE_ENABLED;
            if (!isRegistryEnabled()) return;
        }
        transactionalInfo.transition(transaction);
    }

    @Override
    public void enable(final UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        setModified(transaction);
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
            if (isServiceEnabled()) return;
            state |= SERVICE_ENABLED;
            if (!isRegistryEnabled()) return;
        }
        transactionalInfo.transition(transaction);
    }

    private boolean isServiceEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, SERVICE_ENABLED);
    }

    private boolean isServiceRemoved() {
        assert holdsLock(this);
        return Bits.allAreSet(state, SERVICE_REMOVED);
    }

    void disableRegistry(final Transaction transaction) {
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
            if (!isRegistryEnabled()) return;
            state &= ~REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction);
    }

    void enableRegistry(final Transaction transaction) {
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
            if (isRegistryEnabled()) return;
            state |= REGISTRY_ENABLED;
            if (!isServiceEnabled()) return;
        }
        transactionalInfo.transition(transaction);
    }

    private boolean isRegistryEnabled() {
        assert holdsLock(this);
        return Bits.allAreSet(state, REGISTRY_ENABLED);
    }

    @Override
    public void retry(final UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        setModified(transaction);
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
        }
        transactionalInfo.retry(transaction);
    }

    /**
     * Removes this service.<p>
     * All dependent services will be automatically stopped as the result of this operation.
     *
     * @param  transaction the active transaction
     */
    @Override
    public void remove(final UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        setModified(transaction);
        _remove(transaction);
    }

    void _remove(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        // idempotent
        synchronized (this) {
            if (isServiceRemoved()) return;
            state |= SERVICE_REMOVED;
        }
        initTransactionalInfo(transaction);
        transactionalInfo.scheduleRemoval(transaction);
    }

    @Override
    public void restart(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, primaryRegistration.txnController);
        setModified(transaction);
        initTransactionalInfo(transaction);
        synchronized (this) {
            if (isServiceRemoved()) return;
        }
        transactionalInfo.restart(transaction);
    }

    /**
     * Notifies this service that it is demanded by one of its incoming dependencies.
     * 
     * @param transaction the active transaction
     */
    void demand(final Transaction transaction) {
        initTransactionalInfo(transaction);
        final boolean propagate;
        synchronized (this) {
            if (demandedByCount ++ > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            demandDependencies(transaction);
        }
        transactionalInfo.transition(transaction);
    }

    /**
     * Demands this service's dependencies to start.
     * 
     * @param transaction the active transaction
     */
    private void demandDependencies(Transaction transaction) {
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.demand(transaction);
        }
    }

    /**
     * Notifies this service that it is no longer demanded by one of its incoming dependencies (invoked when incoming
     * dependency is being disabled or removed).
     * 
     * @param transaction the active transaction
     */
    void undemand(final Transaction transaction) {
        initTransactionalInfo(transaction);
        final boolean propagate;
        synchronized (this) {
            if (-- demandedByCount > 0) {
                return;
            }
            propagate = !isMode(MODE_ACTIVE);
        }
        if (propagate) {
            undemandDependencies(transaction);
        }
        transactionalInfo.transition(transaction);
    }

    /**
     * Undemands this service's dependencies to start.
     * 
     * @param transaction the active transaction
     */
    private void undemandDependencies(Transaction transaction) {
        for (DependencyImpl<?> dependency: dependencies) {
            dependency.undemand(transaction);
        }
    }

    public ServiceName getServiceName() {
        return primaryRegistration.getServiceName();
    }

    void dependencySatisfied(final Transaction transaction) {
        initTransactionalInfo(transaction);
        synchronized (ServiceControllerImpl.this) {
            -- unsatisfiedDependencies;
        }
        transactionalInfo.transition(transaction);
    }

    public void dependencyUnsatisfied(final Transaction transaction) {
        initTransactionalInfo(transaction);
        synchronized (this) {
           if (++ unsatisfiedDependencies > 1) {
               return;
            }
        }
        transactionalInfo.transition(transaction);
    }

    /* Transition related methods */

    void setServiceUp(T result) {
        setValue(result);
        transactionalInfo.setTransition(STATE_UP);
    }

    void setServiceFailed() {
        MSCLogger.FAIL.startFailed(getServiceName());
        transactionalInfo.setTransition(STATE_FAILED);
    }

    void setServiceDown() {
        setValue(null);
        transactionalInfo.setTransition(STATE_DOWN);
    }

    void setServiceRemoved(Transaction transaction) {
        clear(transaction);
        transactionalInfo.setTransition(STATE_REMOVED);
    }

    void notifyServiceUp(final Transaction transaction) {
        primaryRegistration.serviceUp(transaction);
        for (Registration registration: aliasRegistrations) {
            registration.serviceUp(transaction);
        }
    }

    void notifyServiceDown(Transaction transaction) {
        primaryRegistration.serviceDown(transaction);
        for (Registration registration: aliasRegistrations) {
            registration.serviceDown(transaction);
        }
    }

    private synchronized void initTransactionalInfo(final Transaction transaction) {
        if (transactionalInfo == null) {
            transactionalInfo = new TransactionalInfo();
            getAbstractTransaction(transaction).addListener(new TerminateCompletionListener() {
                @Override
                public void transactionTerminated() {
                    clean();
                }
            });
        }
    }

    private synchronized  void clean() {
        // prevent hard to find bugs
        assert transactionalInfo.getState() == STATE_UP || transactionalInfo.getState() == STATE_DOWN || transactionalInfo.getState() == STATE_FAILED || transactionalInfo.getState() == STATE_REMOVED: "State: " + transactionalInfo.getState();
        state = (byte) (transactionalInfo.getState() | state & ~STATE_MASK);
        transactionalInfo = null;
    }

    final class TransactionalInfo {
        // current transactional state - must be always set via {@link #setState()} method, not directly
        private volatile byte transactionalState = ServiceControllerImpl.this.currentState();
        // if this service is under transition, this field points to the task that completes the transition
        private TaskController<T> startTask = null;
        private TaskController<Void> stopTask = null;

        void setTransition(byte transactionalState) {
            this.transactionalState = transactionalState;
        }

        private synchronized void retry(Transaction transaction) {
            if (transactionalState != STATE_FAILED) {
                return;
            }
            startTask = StartServiceTask.create(ServiceControllerImpl.this, null, transaction);
        }

        private synchronized void transition(final Transaction transaction) {
            assert !holdsLock(ServiceControllerImpl.this);
            switch (transactionalState) {
                case STATE_STOPPING:
                case STATE_DOWN:
                    if (unsatisfiedDependencies == 0 && shouldStart()) {
                        transactionalState = STATE_STARTING;
                        startTask = StartServiceTask.create(ServiceControllerImpl.this, stopTask, transaction);
                    }
                    break;
                case STATE_FAILED:
                    if ((unsatisfiedDependencies > 0 || shouldStop())) {
                        transactionalState = STATE_STOPPING;
                        StopFailedServiceTask.create(ServiceControllerImpl.this, transaction);
                    }
                    break;
                case STATE_STARTING:
                case STATE_UP:
                    if ((unsatisfiedDependencies > 0 || shouldStop())) {
                        transactionalState = STATE_STOPPING;
                        stopTask = StopServiceTask.create(ServiceControllerImpl.this, startTask, transaction);
                    }
                    break;
                default:
                    break;
            }
        }

        private synchronized void restart(Transaction transaction) {
            if (state == STATE_UP || state == STATE_STARTING) {
                transactionalState = STATE_RESTARTING;
                StopServiceTask.create(ServiceControllerImpl.this, startTask, transaction);
            }
        }

        private synchronized void scheduleRemoval(Transaction transaction) {
            // transition disabled service, guaranteeing that it is either at DOWN state or it will get to this state
            // after complete transition task completes
            transition(transaction);
            RemoveServiceTask.create(ServiceControllerImpl.this, stopTask, transaction);
        }

        private byte getState() {
            return transactionalState;
        }

    }

    private synchronized boolean shouldStart() {
        return (isMode(MODE_ACTIVE) || demandedByCount > 0) && Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED) && Bits.allAreClear(state, SERVICE_REMOVED);
    }

    private synchronized boolean shouldStop() {
        return (isMode(MODE_ON_DEMAND) && demandedByCount == 0) || !Bits.allAreSet(state, SERVICE_ENABLED | REGISTRY_ENABLED) || Bits.allAreSet(state, SERVICE_REMOVED);
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
