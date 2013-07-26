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

package org.jboss.msc._private;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.txn.ServiceContext;

/**
 * A service builder.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceBuilderImpl<T> implements ServiceBuilder<T> {

    static final DependencyFlag[] noFlags = new DependencyFlag[0];

    // the service registry
    private final ServiceRegistryImpl registry;
    // service name
    private final ServiceName name;
    // service aliases
    private final Set<ServiceName> aliases = new HashSet<ServiceName>(0);
    // service itself
    private Service<T> service;
    // dependencies
    private final Map<ServiceName, DependencyImpl<?>> dependencies= new LinkedHashMap<ServiceName, DependencyImpl<?>>();
    // active transaction
    private final TransactionImpl transaction;
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
    ServiceBuilderImpl(final ServiceRegistryImpl registry, final ServiceName name, final TransactionImpl transaction) {
        this.transaction = (TransactionImpl) transaction;
        this.registry = (ServiceRegistryImpl)registry;
        this.name = name;
        this.mode = ServiceMode.ACTIVE;
    }

    ServiceName getServiceName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceBuilder<T> setMode(final ServiceMode mode) {
        checkAlreadyInstalled();
        if (mode == null) {
            MSCLogger.SERVICE.methodParameterIsNull("mode");
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
            MSCLogger.SERVICE.methodParameterIsNull("service");
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
        return addDependencyInternal(registry, name, (DependencyFlag[])null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <D> Dependency<D> addDependency(final ServiceRegistry registry, final ServiceName name, final DependencyFlag... flags) {
        return addDependencyInternal(registry, name, flags);
    }

    private <D> Dependency<D> addDependencyInternal(final ServiceRegistry registry, final ServiceName name, final DependencyFlag... flags) {
        checkAlreadyInstalled();
        if (registry == null) {
            MSCLogger.SERVICE.methodParameterIsNull("registry");
        }
        if (name == null) {
            MSCLogger.SERVICE.methodParameterIsNull("name");
        }
        final Registration dependencyRegistration = ((ServiceRegistryImpl) registry).getOrCreateRegistration(transaction, name);
        final DependencyImpl<D> dependency = new DependencyImpl<D>(dependencyRegistration, transaction, flags != null ? flags : noFlags);
        dependencies.put(name, dependency);
        return dependency;
    }

    void setParentDependency(Registration parentRegistration) {
        dependencies.put(name, new ParentDependency<Void>(parentRegistration, transaction));
    }

    @Override
    public ServiceContext getServiceContext() {
        return new ParentServiceContext(registry.getOrCreateRegistration(transaction, name));
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
    public void install() {
        assert ! calledFromConstructorOf(service) : "install() must not be called from a service constructor";
        // idempotent
        if (installed) {
            return;
        }
        // create primary registration
        final Registration registration = registry.getOrCreateRegistration(transaction, name);

        // create alias registrations
        final ServiceName[] aliasArray = aliases.toArray(new ServiceName[aliases.size()]);
        final Registration[] aliasRegistrations = new Registration[aliasArray.length];
        int i = 0; 
        for (ServiceName alias: aliases) {
            aliasRegistrations[i++] = registry.getOrCreateRegistration(transaction, alias);
        }

        // create dependencies
        final DependencyImpl<?>[] dependenciesArray = new DependencyImpl<?>[dependencies.size()];
        dependencies.values().toArray(dependenciesArray);
        // create and install service controller
        final ServiceController<T> serviceController =  new ServiceController<T>(registration, aliasRegistrations, service, mode, dependenciesArray, transaction);
        serviceController.install(registry, transaction);
        CheckDependencyCycleTask.checkDependencyCycle(serviceController, transaction);
    }
}
