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

package org.jboss.msc.service;

import org.jboss.msc.txn.Transaction;

/**
 * A service context can be used to add new tasks, and to create and remove services and registries.
 * Each one of these operations is controlled by a transaction, which must belong to the same {@code
 * TransactionController} that provided this service context,
 * {@link org.jboss.msc.txn.TransactionController#getServiceContext() directly}
 * or {@link ServiceBuilder#getServiceContext() indirectly}.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface ServiceContext {

    /**
     * Gets a builder which can be used to add a service to {@code registry}.
     *
     * @param valueType   the type of the service value to be added
     * @param registry    the target service registry where new service will be installed
     * @param name        the service name
     * @param transaction the transaction
     * @return the builder for the service
     */
    <T> ServiceBuilder<T> addService(Class<T> valueType, ServiceRegistry registry, ServiceName name, Transaction transaction);

    /**
     * Gets a builder which can be used to add a service to {@code registry}.
     *
     * @param registry    the target service registry where new service will be installed
     * @param name        the service name
     * @param transaction the transaction
     * @return the builder for the service
     */
    ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, Transaction transaction);

    /**
     * Removes a service, causing this service to stop if it is {@code UP}.
     *
     * @param registry    the service registry
     * @param name        the service name
     * @param transaction the transaction
     */
    void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction);

    /**
     * Replaces {@code service} by a new service.
     * 
     * @param valueType   the type of the service value to be added
     * @param service     the service to be replaced
     * @param transaction the transaction
     * @return the builder for the service
     */
    <T> ServiceBuilder<T> replaceService(Class<T> valueType, ServiceController service, Transaction transaction);

    /**
     * Replaces {@code service} by a new service.
     *
     * @param service     the service to be replaced
     * @param transaction the transaction
     * @return the builder for the service
     */
    ServiceBuilder<Void> replaceService(ServiceRegistry registry, ServiceController service, Transaction transaction);

}
