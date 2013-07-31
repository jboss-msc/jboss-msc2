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
package org.jboss.msc._private;

import org.jboss.msc.service.ManagementContext;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.txn.Transaction;
import org.jboss.msc.txn.TransactionController;

/**
 * ManagementContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public final class ManagementContextImpl extends TransactionControllerContext implements ManagementContext {

    public ManagementContextImpl(TransactionController transactionController) {
        super(transactionController);
    }

    @Override
    public void disableService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        ((ServiceRegistryImpl) registry).getRequiredServiceController(name).disableService((TransactionImpl) transaction);
    }

    @Override
    public void enableService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        ((ServiceRegistryImpl) registry).getRequiredServiceController(name).enableService((TransactionImpl) transaction);
    }

    @Override
    public void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        if (registry == null) {
            throw new IllegalArgumentException("registry is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        final Registration registration = ((ServiceRegistryImpl) registry).getRegistration(name);
        if (registration == null) {
            return;
        }
        final ServiceController<?> controller = registration.getController();
        if (controller == null) {
            return;
        }
        controller.remove((TransactionImpl) transaction, ((TransactionImpl) transaction).getTaskFactory());
    }

    @Override
    public void disableRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        ((ServiceRegistryImpl)registry).disable((TransactionImpl) transaction);
    }

    @Override
    public void enableRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        ((ServiceRegistryImpl)registry).enable((TransactionImpl) transaction);
    }

    @Override
    public void removeRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        ((ServiceRegistryImpl)registry).remove((TransactionImpl) transaction);
    }

    @Override
    public void shutdownContainer(ServiceContainer container, Transaction transaction) {
        validateTransaction(transaction);
        assert container instanceof ServiceContainerImpl;
        ((ServiceContainerImpl)container).shutdown((TransactionImpl) transaction);
    }

}
