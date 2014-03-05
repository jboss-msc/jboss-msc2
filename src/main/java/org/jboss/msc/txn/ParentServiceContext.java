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
package org.jboss.msc.txn;

import static org.jboss.msc.txn.Helper.validateTransaction;

import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * Parent service context: behaves just like service context super class except that newly created services are
 * automatically children services of parent.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
class ParentServiceContext<T> extends ServiceContextImpl {

    private final Registration parentRegistration;

    public ParentServiceContext(Registration parentRegistration, TransactionController transactionController) {
        super (transactionController);
        this.parentRegistration = parentRegistration;
    }

    <S> ServiceBuilder<S> addService(final Class<S> valueType, final ServiceRegistry registry, final ServiceName name, final Transaction transaction, final TaskFactory taskFactory) {
        validateParentUp(transaction);
        final ServiceBuilderImpl<S> serviceBuilder = (ServiceBuilderImpl<S>) super.addService(valueType, registry, name, transaction);
        serviceBuilder.setTaskFactory(taskFactory);
        final ServiceName parentName = parentRegistration.getServiceName();
        serviceBuilder.addDependency(parentName, getParentDependency(parentName, parentRegistration, transaction));
        return serviceBuilder;
    }

    ServiceBuilder<Void> addService(final ServiceRegistry registry, final ServiceName name, final Transaction transaction, final TaskFactory taskFactory) {
        validateParentUp(transaction);
        final ServiceBuilderImpl<Void> serviceBuilder = (ServiceBuilderImpl<Void>) super.addService(registry, name, transaction);
        serviceBuilder.setTaskFactory(taskFactory);
        final ServiceName parentName = parentRegistration.getServiceName();
        serviceBuilder.addDependency(parentName, getParentDependency(parentName, parentRegistration, transaction));
        return serviceBuilder;
    }

    private void validateParentUp(final Transaction transaction) {
        if (parentRegistration.getController() == null) {
            throw new IllegalStateException("Service context error: " + parentRegistration.getServiceName() + " is not installed");
        }
        validateTransaction(transaction, parentRegistration.txnController);
        if (!Bits.allAreSet(parentRegistration.getController().getState(transaction), ServiceControllerImpl.STATE_STARTING)) {
            throw new IllegalStateException("Service context error: " + parentRegistration.getServiceName() + " is not starting");
        }
    }

    private static final AttachmentKey<ConcurrentHashMap<ServiceName, DependencyImpl<?>>>  PARENT_DEPENDENCIES= AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceName, DependencyImpl<?>>> () {

        @Override
        public ConcurrentHashMap<ServiceName, DependencyImpl<?>> create() {
            return new ConcurrentHashMap<ServiceName, DependencyImpl<?>>();
        }

    });

    private static final <T> DependencyImpl<T> getParentDependency(ServiceName parentName, Registration parentRegistration, Transaction transaction) {
        final ConcurrentHashMap<ServiceName, DependencyImpl<?>> parentDependencies = transaction.getAttachment(PARENT_DEPENDENCIES);
        @SuppressWarnings("unchecked")
        DependencyImpl<T> parentDependency = (DependencyImpl<T>) parentDependencies.get(parentName);
        if (parentDependency == null ) {
            parentDependency = new ParentDependency<T>(parentRegistration, transaction);
            parentDependencies.put(parentName, parentDependency);
        }
        return parentDependency;
    }

    /**
     * Parent dependency. The dependent is created whenever dependency is satisfied, and is removed whenever
     * dependency is no longer satisfied.
     */
    private static final class ParentDependency<T> extends DependencyImpl<T> {

        ParentDependency(final Registration dependencyRegistration, final Transaction transaction) {
            super(dependencyRegistration, DependencyFlag.UNREQUIRED);
        }

        @Override
        public TaskController<?> dependencyDown(Transaction transaction, TaskFactory taskFactory) {
            return dependent.remove(transaction, taskFactory);
        }
    }
}
