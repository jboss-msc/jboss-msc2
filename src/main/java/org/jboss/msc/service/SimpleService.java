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
package org.jboss.msc.service;

/**
 * Simple service interface: implemented by services that do not need to perform advanced operations on start/stop, such
 * as task creation.
 * <p>
 * Service implementors will have start invoked both on execution phase (when the service is being started) and during
 * rollback (when a stop is being reverted). The same is valid for stop: it could be invoked during both execution
 * and rollback stages of active transaction.
 * 
 * @param T the service value type
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface SimpleService<T> {

    /**
     * Service start  method, invoked on execution and rollback (when needed to revert a previous stop).
     * <p>
     * Implementors must invoke {@link SimpleStartContext#complete(Object) startContext.complete(T)} upon completion.
     * <p>
     * Also, this method cannot be implemented asynchronously.
     * 
     * @param startContext the start context
     */
    public void start(SimpleStartContext<T> startContext);

    /**
     * Service stop method, invoked on execution and rollback (when needed to revert a previous start).
     * <p>
     * Implementors must invoke {@link SimpleStopContext#complete() stopContext.complete()} upon completion.
     * <p>
     * Also, this method cannot be implemented asynchronously.
     * 
     * @param stopContext
     */
    public void stop(SimpleStopContext stopContext);
}
