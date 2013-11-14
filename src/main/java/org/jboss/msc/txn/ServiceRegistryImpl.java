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

import static java.lang.Thread.holdsLock;
import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;

/**
 * A service registry.  Registries can return services by name, or get a collection of service names.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServiceRegistryImpl extends TransactionalObject implements ServiceRegistry {

    private static final byte ENABLED = 1 << 0x00;
    private static final byte REMOVED  = 1 << 0x01;

    // map of service registrations
    private final ConcurrentMap<ServiceName, Registration> registry = new ConcurrentHashMap<ServiceName, Registration>();
    // service registry state, which could be: enabled, disabled, or removed
    private byte state = ENABLED;



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
            lockWrite(transaction);
            registration = new Registration(name);
            Registration appearing = registry.putIfAbsent(name, registration);
            if (appearing != null) {
                registration = appearing;
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
    public void remove(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        synchronized(this) {
            if (Bits.anyAreSet(state, REMOVED)) {
                return;
            }
            state = (byte) (state | REMOVED);
        }
        final HashSet<ServiceControllerImpl<?>> done = new HashSet<ServiceControllerImpl<?>>();
        for (Registration registration : registry.values()) {
            ServiceControllerImpl<?> serviceInstance = registration.getController();
            if (serviceInstance != null && done.add(serviceInstance)) {
                serviceInstance.remove(transaction, transaction.getTaskFactory());
            }
        }
    }

    synchronized void newServiceInstalled(ServiceControllerImpl<?> service, Transaction transaction) {
        checkRemoved();
        if (Bits.anyAreSet(state, ENABLED)) {
            service.enableRegistry(transaction);
        } else {
            service.disableRegistry(transaction);
        }
    }

    @Override
    public synchronized void disable(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        checkRemoved();
        // idempotent
        if (!Bits.anyAreSet(state, ENABLED)) {
            return;
        }
        state = (byte) (state & ~ENABLED);
        for (Registration registration: registry.values()) {
            final ServiceControllerImpl<?> controller = registration.getController();
            if (controller != null) {
                controller.disableRegistry(transaction);
            }
        }
    }

    @Override
    public synchronized void enable(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        checkRemoved();
        // idempotent
        if (Bits.anyAreSet(state, ENABLED)) {
            return;
        }
        state = (byte) (state | ENABLED);
        for (Registration registration: registry.values()) {
            final ServiceControllerImpl<?> controller = registration.getController();
            if (controller != null) {
                controller.enableRegistry(transaction);
            }
        }
    }

    @Override
    Object takeSnapshot() {
        return new Snapshot();
    }

    @Override
    void revert(final Object snapshot) {
        ((Snapshot)snapshot).apply();
    }

    private synchronized void checkRemoved() {
        if (Bits.anyAreSet(state, REMOVED)) {
            throw new IllegalStateException("ServiceRegistry is removed");
        }
    }
    
    private final class Snapshot {
        private final byte state;
        private final Map<ServiceName, Registration> registry;
        
        private Snapshot() {
            assert holdsLock(ServiceRegistryImpl.this);
            state = ServiceRegistryImpl.this.state;
            registry = new HashMap<ServiceName, Registration>(ServiceRegistryImpl.this.registry);
        }
        
        private void apply() {
            assert holdsLock(ServiceRegistryImpl.this);
            ServiceRegistryImpl.this.state = state;
            ServiceRegistryImpl.this.registry.clear();
            ServiceRegistryImpl.this.registry.putAll(registry);
        }
    }
}
