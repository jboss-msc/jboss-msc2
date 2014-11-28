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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import static org.jboss.msc.txn.Helper.setModified;
import static org.jboss.msc.txn.Helper.validateRegistry;

/**
 * ServiceContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
class ServiceContextImpl implements ServiceContext {

    private final UpdateTransaction txn;

    public ServiceContextImpl(final UpdateTransaction txn) {
        this.txn = txn;
    }

    @Override
    public <T> ServiceBuilder<T> addService(final ServiceRegistry registry, final ServiceName name)
    throws IllegalArgumentException, InvalidTransactionStateException {
        validateRegistry(registry);
        if (name == null) {
            MSCLogger.SERVICE.methodParameterIsNull("name");
        }
        setModified(txn);
        return new ServiceBuilderImpl<>(txn, (ServiceRegistryImpl) registry, name);
    }

    UpdateTransaction getTransaction() {
        return txn;
    }

}
