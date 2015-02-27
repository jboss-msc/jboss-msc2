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

    private static final byte ENABLED   = 1 << 0x00;
    private static final byte REMOVED   = 1 << 0x01;
    private static final byte ENABLING  = 1 << 0x02;
    private static final byte DISABLING = 1 << 0x03;

    private long installedServices;
    private NotificationEntry removeObservers;

    final ServiceContainerImpl container;
    // map of service registrations
    private final ConcurrentMap<ServiceName, Registration> registry = new ConcurrentHashMap<>();
    // service registry state, which could be: enabled, disabled, or removed
    private byte state = ENABLED;

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
            registration = new Registration(name, this);
            Registration appearing = registry.putIfAbsent(name, registration);
            if (appearing != null) {
                registration = appearing;
            } else if (Bits.anyAreSet(state, ENABLING | ENABLED)) { // TODO: this is bug - state is accessed without lock being held
                registration.enableRegistry(transaction);
            }
        }
        return registration;
    }

    TransactionController getTransactionController() {
        return container.getTransactionController();
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
        remove(transaction, null);
    }

    @Override
    public void remove(final UpdateTransaction transaction, final Listener<ServiceRegistry> completionListener) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, container.getTransactionController());
        setModified(transaction);
        synchronized (this) {
            if (Bits.allAreClear(state, REMOVED)) {
                final RemoveTask removeTask = new RemoveTask(transaction);
                getAbstractTransaction(transaction).getTaskFactory().newTask(removeTask).release();
                if (completionListener != null) removeObservers = new NotificationEntry(removeObservers, completionListener);
                return; // don't call completion listener
            }
        }
        if (completionListener != null) safeCallListener(completionListener); // open call
    }

    @Override
    public void disable(final UpdateTransaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, container.getTransactionController());
        setModified(transaction);
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            if (Bits.allAreClear(state, ENABLED)) return;
            awaitStateWithoutFlags(ENABLING);
            state |= DISABLING;
        }
        for (Registration registration : registry.values()) { // TODO: this can be moved under lock - no deadlock possibility
            registration.disableRegistry(transaction);
        }
        synchronized (this) {
            state &= ~DISABLING;
            state &= ~ENABLED;
            notifyAll();
        }
    }

    synchronized boolean isEnabled() {
        return Bits.anyAreSet(state, ENABLING | ENABLED);
    }

    @Override
    public void enable(final UpdateTransaction transaction) throws IllegalStateException, IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, container.getTransactionController());
        setModified(transaction);
        synchronized (this) {
            if (Bits.anyAreSet(state, REMOVED)) return;
            if (Bits.anyAreSet(state, ENABLED)) return;
            awaitStateWithoutFlags(DISABLING);
            state |= ENABLING;
        }
        for (Registration registration : registry.values()) { // TODO: this can be moved under lock - no deadlock possibility
            registration.enableRegistry(transaction);
        }
        synchronized (this) {
            state &= ~ENABLING;
            state |= ENABLED;
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

    void safeCallListener(final Listener<ServiceRegistry> listener) {
        try {
            listener.handleEvent(this);
        } catch (final Throwable t) {
            MSCLogger.SERVICE.serviceRegistryCompletionListenerFailed(t);
        }
    }

    synchronized void serviceInstalled() {
        ++installedServices;
    }

    void serviceRemoved() {
        NotificationEntry disableObservers, enableObservers, removeObservers;
        synchronized (this) {
            if (--installedServices != 0) return;
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
