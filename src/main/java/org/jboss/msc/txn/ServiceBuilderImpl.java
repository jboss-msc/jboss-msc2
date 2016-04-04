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
import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jboss.msc.txn.Helper.validateTransaction;

/**
 * A service builder. Implementations of this class are not thread safe. They cannot be shared across threads.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl<T> implements ServiceBuilder<T> {

    private static final Registration[] NO_ALIASES = new Registration[0];
    private static final DependencyImpl<?>[] NO_DEPENDENCIES = new DependencyImpl<?>[0];

    static final DependencyFlag[] noFlags = new DependencyFlag[0];

    // the service registry
    private final ServiceRegistryImpl registry;
    // service name
    private final ServiceName name;
    // service aliases
    private final Set<ServiceName> aliases = new HashSet<>(0);
    // service itself
    private Service<T> service;
    // dependencies
    private final Map<DependencyKey, DependencyImpl<?>> dependencies = new HashMap<>();
    // active transaction
    private final UpdateTransaction transaction;
    // service mode
    private ServiceMode mode;
    // is service builder installed?
    private boolean installed;

    /**
     * Creates service builder.
     * @param registry     the service registry
     * @param name         service name
     * @param transaction  active transaction
     */
    ServiceBuilderImpl(final UpdateTransaction transaction, final ServiceRegistryImpl registry, final ServiceName name) {
        this.transaction = transaction;
        this.registry = registry;
        this.name = name;
        this.mode = ServiceMode.ACTIVE;
    }

    void addDependency(final ServiceRegistryImpl registry, final ServiceName name, final DependencyImpl<?> dependency) {
        dependencies.put(new DependencyKey(registry, name), dependency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceBuilder<T> setMode(final ServiceMode mode) {
        checkAlreadyInstalled();
        if (mode == null) {
            throw MSCLogger.SERVICE.methodParameterIsNull("mode");
        }
        this.mode = mode;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceBuilder<T> setService(final Service<T> service) {
        assert ! calledFromConstructorOf(service) : "setService() must not be called from the service constructor";
        checkAlreadyInstalled();
        if (service == null) {
            throw MSCLogger.SERVICE.methodParameterIsNull("service");
        }
        this.service = service;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceBuilderImpl<T> addAliases(final ServiceName... aliases) {
        checkAlreadyInstalled();
        if (aliases != null) for (final ServiceName alias : aliases) {
            if (alias != null && !alias.equals(name)) {
                this.aliases.add(alias);
            }
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <D> Dependency<D> addDependency(final ServiceName name) {
        return addDependencyInternal(registry, name, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <D> Dependency<D> addDependency(final ServiceName name, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, flags);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <D> Dependency<D> addDependency(final ServiceRegistry registry, final ServiceName name) {
        return addDependencyInternal((ServiceRegistryImpl)registry, name, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <D> Dependency<D> addDependency(final ServiceRegistry registry, final ServiceName name, final DependencyFlag... flags) {
        return addDependencyInternal((ServiceRegistryImpl)registry, name, flags);
    }

    private <D> Dependency<D> addDependencyInternal(final ServiceRegistryImpl registry, final ServiceName name, final DependencyFlag... flags) {
        checkAlreadyInstalled();
        if (registry == null) {
            throw MSCLogger.SERVICE.methodParameterIsNull("registry");
        }
        if (name == null) {
            throw MSCLogger.SERVICE.methodParameterIsNull("name");
        }
        if (this.registry.getTransactionController() != registry.getTransactionController()) {
            throw MSCLogger.SERVICE.cannotCreateDependencyOnRegistryCreatedByOtherTransactionController();
        }
        final DependencyKey key = new DependencyKey(registry, name);
        final DependencyImpl<D> dependency = new DependencyImpl<>(flags != null ? flags : noFlags);
        dependencies.put(key, dependency);
        return dependency;
    }

    private static final class DependencyKey {
        private final ServiceRegistryImpl registry;
        private final ServiceName name;

        private DependencyKey(final ServiceRegistryImpl registry, final ServiceName name) {
            this.registry = registry;
            this.name = name;
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + System.identityHashCode(registry);
            result = 37 * result + name.hashCode();
            return result;
        }

        @Override
        public boolean equals(final Object o) {
            if (o == null) return false;
            if (!(o instanceof DependencyKey)) return false;
            final DependencyKey other = (DependencyKey)o;
            return this.registry == other.registry && this.name.equals(other.name);
        }
    }

    private static boolean calledFromConstructorOf(Object obj) {
        if (obj == null) return false;
        final String c = obj.getClass().getName();
        final StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            if (element.getMethodName().equals("<init>") && element.getClassName().equals(c)) {
                return true;
            }
        }
        return false;
    }

    private void checkAlreadyInstalled() {
        if (installed) {
            throw new IllegalStateException("ServiceBuilder installation already requested.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceController<T> install() throws IllegalStateException, DuplicateServiceException, CircularDependencyException {
        assert ! calledFromConstructorOf(service) : "install() must not be called from a service constructor";
        // idempotent
        validateTransaction(transaction, registry.getTransactionController());
        if (installed) {
            throw MSCLogger.SERVICE.cannotCallInstallTwice();
        }
        final TransactionHoldHandle handle = transaction.acquireHoldHandle();
        installed = true;
        try {
            // create primary registration
            final Registration registration = registry.getOrCreateRegistration(name);

            // create alias registrations
            final Registration[] aliasRegistrations = aliases.size() > 0 ? new Registration[aliases.size()] : NO_ALIASES;
            if (aliasRegistrations.length > 0) {
                int i = 0;
                for (final ServiceName alias : aliases) {
                    aliasRegistrations[i++] = registry.getOrCreateRegistration(alias);
                }
            }

            // create dependencies
            final DependencyImpl<?>[] dependenciesArray = dependencies.size() > 0 ? new DependencyImpl<?>[dependencies.size()] : NO_DEPENDENCIES;
            if (dependenciesArray.length > 0) {
                dependencies.values().toArray(dependenciesArray);
                for (final Map.Entry<DependencyKey, DependencyImpl<?>> e : dependencies.entrySet()) {
                    e.getValue().setDependencyRegistration(e.getKey().registry.getOrCreateRegistration(e.getKey().name));
                }
            }

            // create and install service controller
            final ServiceControllerImpl<T> serviceController = new ServiceControllerImpl<>(registration, aliasRegistrations, service, mode, dependenciesArray);
            serviceController.beginInstallation();
            try {
                serviceController.completeInstallation(transaction);
            } catch (Throwable t) {
                serviceController.clear(transaction);
                throw t;
            }
            return serviceController;
        } finally {
            handle.release();
        }
    }
}
