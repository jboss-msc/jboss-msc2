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

import static org.jboss.msc._private.MSCLogger.SERVICE;

import org.jboss.msc.service.ManagementContext;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * ManagementContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class ManagementContextImpl extends TransactionControllerContext implements ManagementContext {

    public ManagementContextImpl(TransactionController transactionController) {
        super(transactionController);
    }

    @Override
    public void disableService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        ((ServiceRegistryImpl) registry).getRequiredServiceController(name).disableService(transaction);
    }

    @Override
    public void enableService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        ((ServiceRegistryImpl) registry).getRequiredServiceController(name).enableService(transaction);
    }

    @Override
    public void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        if (name == null) {
            throw SERVICE.methodParameterIsNull("name");
        }
        final Registration registration = ((ServiceRegistryImpl) registry).getRegistration(name);
        if (registration == null) {
            return;
        }
        final ServiceController<?> controller = registration.getController();
        if (controller == null) {
            return;
        }
        controller.remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public void disableRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        ((ServiceRegistryImpl)registry).disable(transaction);
    }

    @Override
    public void enableRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        ((ServiceRegistryImpl)registry).enable(transaction);
    }

    @Override
    public void removeRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        ((ServiceRegistryImpl)registry).remove(transaction);
    }

    @Override
    public void shutdownContainer(ServiceContainer container, Transaction transaction) {
        validateTransaction(transaction);
        if (container == null) {
            throw new IllegalArgumentException("container is null");
        }
        if (!(container instanceof ServiceContainerImpl)) {
            throw new IllegalArgumentException("invalid container");
        }
        ((ServiceContainerImpl)container).shutdown(transaction);
    }

}
