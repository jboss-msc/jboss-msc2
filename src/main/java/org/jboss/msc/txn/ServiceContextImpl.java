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

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc.txn.Helper.setModified;
import static org.jboss.msc.txn.Helper.validateRegistry;
import static org.jboss.msc.txn.Helper.validateTransaction;

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
    public <T> ServiceBuilder<T> addService(final UpdateTransaction transaction, final ServiceRegistry registry, final ServiceName name)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        validateTransaction(transaction, txnController);
        validateTransaction(transaction, ((ServiceRegistryImpl)registry).txnController);
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        setModified(transaction);
        return new ServiceBuilderImpl<>(txnController, (ServiceRegistryImpl) registry, name, transaction);
    }

    @Override
    public void removeService(final UpdateTransaction transaction, final ServiceRegistry registry, final ServiceName name)
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
        setModified(transaction);
        controller.remove(transaction);
    }

    @Override
    public <T> ServiceBuilder<T> replaceService(final UpdateTransaction transaction, final ServiceController service)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(transaction, txnController);
        // TODO implement
        throw new RuntimeException("not implemented");
    }

}
