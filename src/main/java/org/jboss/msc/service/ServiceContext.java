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

import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.UpdateTransaction;

/**
 * A service context can be used to add new services.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface ServiceContext {

    /**
     * Gets a builder which can be used to add a service to {@code registry}.
     *
     * @param registry    the target service registry where new service will be installed
     * @param name        the service name
     * @return the builder for the service
     * @throws java.lang.IllegalArgumentException if any method parameter is <code>null</code>
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service context and with <code>registry</code>.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    <T> ServiceBuilder<T> addService(ServiceRegistry registry, ServiceName name)
    throws IllegalArgumentException, InvalidTransactionStateException;

}
