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

import static org.jboss.msc._private.MSCLogger.TXN;
import static org.jboss.msc.txn.Helper.validateTransaction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

/**
 * A service registry.  Registries can return services by name, or get a collection of service names.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServiceRegistryImpl extends ServiceManager implements ServiceRegistry {

    private static final byte ENABLED = 1 << 0x00;
    private static final byte REMOVED  = 1 << 0x01;

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
            checkRemoved();
            registration = new Registration(name, txnController);
            Registration appearing = registry.putIfAbsent(name, registration);
            if (appearing != null) {
                registration = appearing;
            } else if (Bits.anyAreSet(state, ENABLED)){
                registration.enableRegistry(transaction, transaction.getTaskFactory());
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
    public void remove(final Transaction transaction) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) {
                return;
            }
        }
        final RemoveTask removeTask = new RemoveTask(transaction);
        transaction.getTaskFactory().newTask(removeTask).setRevertible(removeTask).release();
    }

    @Override
    public void disable(final Transaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        checkRemoved();
        super.disable(transaction);
    }

    boolean doDisable(Transaction transaction, TaskFactory taskFactory) {
        synchronized (this) {
            // idempotent
            if (!Bits.anyAreSet(state, ENABLED)) {
                return false;
            }
            state = (byte) (state & ~ENABLED);
        }
        for (Registration registration: registry.values()) {
            registration.disableRegistry(transaction, taskFactory);
        }
        return true;
    }

    @Override
    public void enable(final Transaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        checkRemoved();
        super.enable(transaction);
    }

    boolean doEnable(Transaction transaction, TaskFactory taskFactory) {
        synchronized (this) {
            // idempotent
            if (Bits.anyAreSet(state, ENABLED)) {
                return false;
            }
            state = (byte) (state | ENABLED);
        }
        for (Registration registration: registry.values()) {
            registration.enableRegistry(transaction, taskFactory);
        }
        return true;
    }

    private synchronized void checkRemoved() throws IllegalStateException {
        if (Bits.anyAreSet(state, REMOVED)) {
            throw TXN.removedServiceRegistry();
        }
    }

    private final class RemoveTask implements Executable<Void>, Revertible {

        private final Transaction transaction;
        private boolean removed;

        public RemoveTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                synchronized(ServiceRegistryImpl.this) {
                    if (Bits.anyAreSet(state, REMOVED)) {
                        return;
                    }
                    state = (byte) (state | REMOVED);
                }
                for (Registration registration : registry.values()) {
                    registration.remove(transaction, context);
                }
                removed = true;
            } finally {
                context.complete();
            }
        }

        @Override
        public synchronized void rollback(RollbackContext context) {
            try {
                if (!removed ) {
                    return;
                }
                synchronized (ServiceRegistryImpl.this) {
                    state = (byte) (state & ~REMOVED);
                }
                for (Registration registration : registry.values()) {
                    registration.reinstall(transaction);
                }
            } finally {
                context.complete();
            }
        }
    }
}
