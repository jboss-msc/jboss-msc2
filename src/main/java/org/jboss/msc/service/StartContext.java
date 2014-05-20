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

import org.jboss.msc.txn.WorkContext;

/**
 * Service start lifecycle context.
 *
 * @param <T> the service value type
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface StartContext<T> extends WorkContext<T> {

    /**
     * Marking completion of this service start failed.
     */
    void fail();

    /**
     * Start installation of a child service into {@code registry}.
     *
     * @param valueType      the type of the service value to be added
     * @param registry       the target service registry where new service will be installed
     * @param name           the service name
     * @param serviceContext the parent service context
     * @return the builder for the service
     */
    <S> ServiceBuilder<S> addService(Class<S> valueType, ServiceRegistry registry, ServiceName name, ServiceContext parentContext);

    /**
     * Start installation of a child service into {@code registry}.
     *
     * @param registry       the target service registry where new service will be installed
     * @param name           the service name
     * @param serviceContext the parent service context
     * @return the builder for the service
     */
    ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, ServiceContext parentContext);
}
