/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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
package org.jboss.msc.test.utils;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.test.utils.TestService.DependencyInfo;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.ServiceController;
import org.jboss.msc.txn.TransactionController;

/**
 * A TestServiceBuilder allows building a TestService for more complex test scenarios, where the transaction is provided by the
 * test rather than being transparently created and committed during service installation.
 * 
 * For simple test scenarios, invoke {@link AbstractServiceTest#addService(ServiceName, ServiceName...)} or one of its
 * overloaded options.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * 
 */
public class TestServiceBuilder {

    private static TransactionController txnController;
    private static ServiceRegistry registry;

    public static void setDefaultServiceRegistry(ServiceRegistry registry) {
        TestServiceBuilder.registry = registry;
    }

    public static void setDefaultTransactionController(TransactionController txnController) {
        TestServiceBuilder.txnController = txnController;
    }

    private static final DependencyInfo<?>[] no_dependencies = new DependencyInfo[0];
    private final TestService service;
    private final ServiceBuilder<Void> serviceBuilder;

    private static DependencyInfo<?>[] createDependencyInfos(ServiceName[] dependencies) {
        DependencyInfo<?>[] dependencyInfos = new DependencyInfo<?>[dependencies.length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencyInfos[i] = new DependencyInfo<Void>(dependencies[i]);
        }
        return dependencyInfos;
    }

    public TestServiceBuilder(BasicTransaction txn, final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode, final ServiceName... dependencies) {
        this(txn, serviceRegistry, serviceName, failToStart, serviceMode, createDependencyInfos(dependencies));
    }

    public TestServiceBuilder(BasicTransaction txn, final ServiceName serviceName, final boolean failToStart,
            final ServiceMode serviceMode, final ServiceName... dependencies) {
        this(txn, registry, serviceName, failToStart, serviceMode, dependencies);
    }

    public TestServiceBuilder(BasicTransaction txn, final ServiceName serviceName, final ServiceMode serviceMode,
            final ServiceName... dependencies) {
        this(txn, registry, serviceName, false, serviceMode, dependencies);
    }

    public TestServiceBuilder(BasicTransaction txn, final ServiceName serviceName, final boolean failToStart,
            final ServiceName... dependencies) {
        this(txn, registry, serviceName, failToStart, null, dependencies);
    }

    public TestServiceBuilder(BasicTransaction txn, final ServiceName serviceName, final ServiceName... dependencies) {
        this(txn, registry, serviceName, false, null, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode, final DependencyInfo<?>... dependencies) {
        this(txn, txnController.getServiceContext(), serviceRegistry, serviceName, failToStart, serviceMode, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, ServiceContext serviceContext, final ServiceRegistry serviceRegistry,
            final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode,
            final DependencyInfo<?>... dependencies) {
        // create service builder
        this.serviceBuilder = serviceContext.addService(serviceRegistry, serviceName, txn);
        // create test service (dependency infos will be used by the service to add dependencies to servicebuilder and keep the
        // resulting Dependency object)
        this.service = new TestService(serviceName, serviceBuilder, failToStart, dependencies);
        serviceBuilder.setService(service);
        // set mode
        if (serviceMode != null)
            serviceBuilder.setMode(serviceMode);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final boolean failToStart,
            final ServiceMode serviceMode, final DependencyInfo<?>... dependencies) {
        this(txn, registry, serviceName, failToStart, serviceMode, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final ServiceMode serviceMode,
            final DependencyInfo<?>... dependencies) {
        this(txn, registry, serviceName, false, serviceMode, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final boolean failToStart,
            final DependencyInfo<?>... dependencies) {
        this(txn, registry, serviceName, failToStart, null, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName,
            final DependencyInfo<?>... dependencies) {
        this(txn, registry, serviceName, false, null, dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final boolean failToStart,
            final ServiceMode serviceMode) {
        this(txn, registry, serviceName, failToStart, serviceMode, no_dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final ServiceMode serviceMode) {
        this(txn, registry, serviceName, false, serviceMode, no_dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName, final boolean failToStart) {
        this(txn, registry, serviceName, failToStart, null, no_dependencies);
    }

    public TestServiceBuilder(final BasicTransaction txn, final ServiceName serviceName) {
        this(txn, registry, serviceName, false, null, no_dependencies);
    }

    public ServiceController install() {
        return serviceBuilder.install();
    }

    public TestService getService() {
        return service;
    }
}
