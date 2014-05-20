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
import static org.jboss.msc.txn.Helper.validateRegistry;
import static org.jboss.msc.txn.Helper.validateTransaction;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

/**
 * ServiceContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
class ServiceContextImpl implements ServiceContext {

    private final TransactionController txnController;

    public ServiceContextImpl(final TransactionController txnController) {
        this.txnController = txnController;
    }

    @Override
    public <T> ServiceBuilder<T> addService(final Class<T> valueType, final ServiceRegistry registry, final ServiceName name, final Transaction transaction)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        
        return new ServiceBuilderImpl<>(txnController, (ServiceRegistryImpl) registry, name, transaction);
    }

    @Override
    public void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        if (name == null) {
            throw SERVICE.methodParameterIsNull("name");
        }
        final Registration registration = ((ServiceRegistryImpl) registry).getRegistration(name);
        if (registration == null) {
            return;
        }
        final ServiceControllerImpl<?> controller = registration.getController();
        if (controller == null) {
            return;
        }
        controller.remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, Transaction transaction)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return new ServiceBuilderImpl<>(txnController, (ServiceRegistryImpl) registry, name, transaction);
    }

    @Override
    public <T> ServiceBuilder<T> replaceService(Class<T> valueType, ServiceRegistry registry, ServiceController service, Transaction transaction)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        // TODO implement
        throw new RuntimeException("not implemented");
    }

    @Override
    public ServiceBuilder<Void> replaceService(ServiceRegistry registry, ServiceController service, Transaction transaction)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        // TODO implement
        throw new RuntimeException("not implemented");
    }

}
