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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.util.Listener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final byte ENABLED = 1;
    private static final byte REMOVED = 2;

    // service registry state, which could be: enabled, disabled, or removed
    private byte state = ENABLED;
    private long installedServices;
    private NotificationEntry removeObservers;

    final ServiceContainerImpl container;
    private final Object lock = new Object();
    // map of service registrations
    private final ConcurrentMap<ServiceName, Registration> registry = new ConcurrentHashMap<>();

    ServiceRegistryImpl(final ServiceContainerImpl container) {
        this.container = container;
    }

    /**
     * Gets a service, throwing an exception if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}
     * @throws ServiceNotFoundException if the service is not present in the registry
     */
    @SuppressWarnings("unchecked")
    public <T> ServiceController<T> getRequiredService(final ServiceName serviceName) throws ServiceNotFoundException {
        if (serviceName == null) {
            throw TXN.methodParameterIsNull("serviceName");
        }
        return (ServiceController<T>) getRequiredServiceController(serviceName);
    }

    /**
     * Gets a service, returning {@code null} if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}, or {@code null} if it is not found
     */
    @SuppressWarnings("unchecked")
    public <T> ServiceController<T> getService(final ServiceName serviceName) {
        if (serviceName == null) {
            throw TXN.methodParameterIsNull("serviceName");
        }
        final Registration registration = registry.get(serviceName);
        if (registration == null) {
            return null;
        }
        return (ServiceController<T>) registration.getController();
    }

    Registration getOrCreateRegistration(final ServiceName name) {
        synchronized (lock) {
            if (Bits.anyAreSet(state, REMOVED)) {
                throw TXN.removedServiceRegistry();
            }
            Registration registration = registry.get(name);
            if (registration == null) {
                registration = new Registration(name, this);
                Registration appearing = registry.putIfAbsent(name, registration);
                if (appearing != null) {
                    registration = appearing;
                }
            }
            return registration;
        }
    }

    TransactionController getTransactionController() {
        return container.getTransactionController();
    }

    ServiceControllerImpl<?> getRequiredServiceController(final ServiceName serviceName) throws ServiceNotFoundException {
        final Registration r;
        synchronized (lock) {
            r = registry.get(serviceName);
        }
        if (r == null || r.getController() == null) {
            throw new ServiceNotFoundException("Service " + serviceName + " not found");
        }
        return r.getController();
    }

    @Override
    public void remove(final UpdateTransaction txn) throws IllegalArgumentException, InvalidTransactionStateException {
        remove(txn, null);
    }

    @Override
    public void remove(final UpdateTransaction txn, final Listener<ServiceRegistry> completionListener) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(txn, container.getTransactionController());
        final TransactionHoldHandle txnHoldHandle = txn.acquireHoldHandle();
        try {
            setModified(txn);
            synchronized (lock) {
                if (Bits.allAreClear(state, REMOVED)) {
                    state = (byte) (state | REMOVED);
                    if (installedServices > 0) {
                        final RemoveTask removeTask = new RemoveTask(txn);
                        getAbstractTransaction(txn).getTaskFactory().newTask(removeTask).release();
                        if (completionListener != null) {
                            removeObservers = new NotificationEntry(removeObservers, completionListener);
                        }
                        return; // don't call completion listener
                    }
                    container.registryRemoved();
                }
            }
            if (completionListener != null) safeCallListener(completionListener); // open call
        } finally {
            txnHoldHandle.release();
        }
    }

    @Override
    public void disable(final UpdateTransaction txn) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(txn, container.getTransactionController());
        final TransactionHoldHandle txnHoldHandle = txn.acquireHoldHandle();
        try {
            setModified(txn);
            synchronized (lock) {
                if (Bits.anyAreSet(state, REMOVED)) return;
                if (Bits.allAreClear(state, ENABLED)) return;
                state &= ~ENABLED;
                for (final Registration registration : registry.values()) {
                    registration.disableRegistry(txn);
                }
            }
        } finally {
            txnHoldHandle.release();
        }
    }

    boolean isEnabled() {
        synchronized (lock) {
            return Bits.anyAreSet(state, ENABLED);
        }
    }

    @Override
    public void enable(final UpdateTransaction txn) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(txn, container.getTransactionController());
        final TransactionHoldHandle txnHoldHandle = txn.acquireHoldHandle();
        try {
            setModified(txn);
            synchronized (lock) {
                if (Bits.anyAreSet(state, REMOVED)) return;
                if (Bits.anyAreSet(state, ENABLED)) return;
                state |= ENABLED;
                for (final Registration registration : registry.values()) {
                    registration.enableRegistry(txn);
                }
            }
        } finally {
            txnHoldHandle.release();
        }
    }

    private final class RemoveTask implements Executable<Void> {

        private final Transaction txn;

        public RemoveTask(final Transaction txn) {
            this.txn = txn;
        }

        @Override
        public void execute(final ExecuteContext<Void> context) {
            try {
                synchronized (ServiceRegistryImpl.this.lock) {
                    for (final Registration registration : registry.values()) {
                        registration.remove(txn);
                    }
                    registry.clear();
                }
            } finally {
                context.complete();
            }
        }
    }

    void safeCallListener(final Listener<ServiceRegistry> listener) {
        try {
            listener.handleEvent(this);
        } catch (final Throwable t) {
            MSCLogger.SERVICE.serviceRegistryCompletionListenerFailed(t);
        }
    }

    void serviceInstalled() {
        synchronized (lock) {
            ++installedServices;
        }
    }

    void serviceRemoved() {
        NotificationEntry removeObservers;
        synchronized (lock) {
            if (--installedServices > 0) return;
            removeObservers = this.removeObservers;
            this.removeObservers = null;
        }
        while (removeObservers != null) {
            safeCallListener(removeObservers.completionListener);
            removeObservers = removeObservers.next;
        }
        container.registryRemoved();
    }

    private static final class NotificationEntry {

        private final NotificationEntry next;
        private final Listener<ServiceRegistry> completionListener;

        private NotificationEntry(final NotificationEntry next, final Listener<ServiceRegistry> completionListener) {
            this.next = next;
            this.completionListener = completionListener;
        }

    }

}
