/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.lang.Thread.holdsLock;
import static org.jboss.msc._private.MSCLogger.TXN;
import static org.jboss.msc.txn.Helper.getAbstractTransaction;
import static org.jboss.msc.txn.Helper.setModified;
import static org.jboss.msc.txn.Helper.validateTransaction;

/**
 * A service registry.  Registries can return services by name, or get a collection of service names.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServiceRegistryImpl implements ServiceRegistry {

    private static final byte ENABLED = 1 << 0x00;
    private static final byte REMOVED  = 1 << 0x01;
    private static final byte ENABLING = 1 << 0x02;
    private static final byte DISABLING = 1 << 0x03;

    final TransactionController txnController;
    // map of service registrations
    private final ConcurrentMap<ServiceName, Registration> registry = new ConcurrentHashMap<>();
    // service registry state, which could be: enabled, disabled, or removed
    private byte state = ENABLED;

    ServiceRegistryImpl(final TransactionController txnController) {
        this.txnController = txnController;
    }

    /**
     * Gets a service, throwing an exception if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}
     * @throws ServiceNotFoundException if the service is not present in the registry
     */
    public ServiceController getRequiredService(final ServiceName serviceName) throws ServiceNotFoundException {
        if (serviceName == null) {
            throw TXN.methodParameterIsNull("serviceName");
        }
        return getRequiredServiceController(serviceName);
    }

    /**
     * Gets a service, returning {@code null} if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}, or {@code null} if it is not found
     */
    public ServiceController getService(final ServiceName serviceName) {
        if (serviceName == null) {
            throw TXN.methodParameterIsNull("serviceName");
        }
        final Registration registration = registry.get(serviceName);
        if (registration == null) {
            return null;
        }
        return registration.getController();
    }

    Registration getOrCreateRegistration(Transaction transaction, ServiceName name) {
        Registration registration = registry.get(name);
        if (registration == null) {
            synchronized (this) { checkRemoved(); }
            registration = new Registration(name, txnController);
            Registration appearing = registry.putIfAbsent(name, registration);
            if (appearing != null) {
                registration = appearing;
            } else if (Bits.anyAreSet(state, ENABLED)){
                registration.enableRegistry(transaction);
            }
        }
        return registration;
    }

    Registration getRegistration(ServiceName name) {
        return registry.get(name);
    }

    ServiceControllerImpl<?> getRequiredServiceController(ServiceName serviceName) throws ServiceNotFoundException {
        final ServiceControllerImpl<?> controller = registry.containsKey(serviceName)? registry.get(serviceName).getController(): null;
        if (controller == null) {
            throw new ServiceNotFoundException("Service " + serviceName + " not found");
        }
        return controller;
    }

    @Override
    public void remove(final UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) {
                return;
            }
        }
        setModified(transaction);
        final RemoveTask removeTask = new RemoveTask(transaction);
        getAbstractTransaction(transaction).getTaskFactory().newTask(removeTask).release();
    }

    @Override
    public void disable(final UpdateTransaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        setModified(transaction);
        synchronized (this) {
            checkRemoved();
            if (!Bits.anyAreSet(state, ENABLED)) return; // idempotent
            awaitStateWithoutFlags(ENABLING);
            state &= ~ENABLED;
            state |= DISABLING;
        }
        for (Registration registration: registry.values()) {
            registration.disableRegistry(transaction);
        }
        synchronized (this) {
            state &= ~DISABLING;
            notifyAll();
        }
    }

    @Override
    public void enable(final UpdateTransaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        setModified(transaction);
        synchronized (this) {
            checkRemoved();
            if (Bits.anyAreSet(state, ENABLED)) return; // idempotent
            awaitStateWithoutFlags(DISABLING);
            state |= ENABLED;
            state |= ENABLING;
        }
        for (Registration registration: registry.values()) {
            registration.enableRegistry(transaction);
        }
        synchronized (this) {
            state &= ~ENABLING;
            notifyAll();
        }
    }

    private void checkRemoved() throws IllegalStateException {
        assert holdsLock(this);
        if (Bits.anyAreSet(state, REMOVED)) {
            throw TXN.removedServiceRegistry();
        }
    }

    private final class RemoveTask implements Executable<Void> {

        private final Transaction transaction;

        public RemoveTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                synchronized (ServiceRegistryImpl.this) {
                    if (Bits.anyAreSet(state, REMOVED)) {
                        return;
                    }
                    state = (byte) (state | REMOVED);
                    awaitStateWithoutFlags(ENABLING | DISABLING);
                }
                for (Registration registration : registry.values()) {
                    registration.remove(transaction);
                }
                registry.clear();
            } finally {
                context.complete();
            }
        }
    }

    private void awaitStateWithoutFlags(final int flags) {
        assert holdsLock(this);
        boolean interrupted = false;
        try {
            while (Bits.anyAreSet(state, flags)) {
                try {
                    wait();
                } catch (final InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
