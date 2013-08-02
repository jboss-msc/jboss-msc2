/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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
 * A service registry.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface ServiceRegistry {

    /**
     * Gets a service, throwing an exception if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}
     * @throws ServiceNotFoundException if the service is not present in the registry
     */
    Service<?> getRequiredService(ServiceName serviceName) throws ServiceNotFoundException;

    /**
     * Gets a service, returning {@code null} if it is not found.
     *
     * @param serviceName the service name
     * @return the service corresponding to {@code serviceName}, or {@code null} if it is not found
     */
    Service<?> getService(ServiceName serviceName);

    /**
     * Disables a service, causing this service to stop if it is {@code UP}.
     *
     * @param name        the service name
     * @param transaction the transaction
     */
    void disableService(ServiceName name, Transaction transaction);

    /**
     * Enables the service, which may start as a result, according to its {@link org.jboss.msc.service.ServiceMode mode} rules.
     * <p> Services are enabled by default.
     *
     * @param name        the service name
     * @param transaction the transaction
     */
    void enableService(ServiceName name, Transaction transaction);

    /**
     * Disables this registry and all its services, causing {@code UP} services to stop.
     *
     * @param transaction the transaction
     */
    void disable(Transaction transaction);

    /**
     * Enables this registry. As a result, its services may start, depending on their
     * {@link org.jboss.msc.service.ServiceMode mode} rules.
     * <p> Registries are enabled by default.
     *
     * @param transaction the transaction
     */
    void enable(Transaction transaction);

    /**
     * Removes this registry from the container.
     *
     * @param transaction the transaction
     */
    void remove(Transaction transaction);
}
