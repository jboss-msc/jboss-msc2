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

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.util.AttachmentKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Parent service context: behaves just like service context super class except that newly created services are
 * automatically children services of parent.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
class ParentServiceContext extends ServiceContextImpl {

    private static final AttachmentKey<ConcurrentHashMap<ServiceName, DependencyImpl<?>>> PARENT_DEPENDENCIES= AttachmentKey.create();

    private final Registration parentRegistration;

    public ParentServiceContext(Registration parentRegistration, UpdateTransaction txn) {
        super (txn);
        this.parentRegistration = parentRegistration;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ServiceBuilder<S> addService(final ServiceRegistry registry, final ServiceName name) {
        validateParentUp();
        final ServiceBuilderImpl<S> serviceBuilder = (ServiceBuilderImpl<S>) super.addService(registry, name);
        final ServiceName parentName = parentRegistration.getServiceName();
        serviceBuilder.addDependency(parentRegistration.registry, parentName, getParentDependency(parentName));
        return serviceBuilder;
    }

    private void validateParentUp() {
        if (parentRegistration.getController() == null) {
            throw new IllegalStateException("Service context error: " + parentRegistration.getServiceName() + " is not installed");
        }
        if (!Bits.allAreSet(parentRegistration.getController().getState(), ServiceControllerImpl.STATE_STARTING)) {
            throw new IllegalStateException("Service context error: " + parentRegistration.getServiceName() + " is not starting");
        }
    }

    private <T> DependencyImpl<T> getParentDependency(ServiceName parentName) {
        ConcurrentHashMap<ServiceName, DependencyImpl<?>> parentDependencies = getTransaction().getAttachment(PARENT_DEPENDENCIES);
        if (parentDependencies == null) {
            getTransaction().putAttachmentIfAbsent(PARENT_DEPENDENCIES, new ConcurrentHashMap<ServiceName, DependencyImpl<?>>());
            parentDependencies = getTransaction().getAttachment(PARENT_DEPENDENCIES);
        }
        @SuppressWarnings("unchecked")
        DependencyImpl<T> parentDependency = (DependencyImpl<T>) parentDependencies.get(parentName);
        if (parentDependency == null ) {
            parentDependency = new ParentDependency<>();
            parentDependencies.put(parentName, parentDependency);
        }
        return parentDependency;
    }

    /**
     * Parent dependency. The dependent is created whenever dependency is satisfied, and is removed whenever
     * dependency is no longer satisfied.
     */
    private static final class ParentDependency<T> extends DependencyImpl<T> {

        ParentDependency() {
            super(DependencyFlag.UNREQUIRED);
        }

        @Override
        public void dependencyDown(Transaction transaction) {
            dependent._remove(transaction, null);
        }
    }
}
