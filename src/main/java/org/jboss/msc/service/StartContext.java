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

/**
 * Service start lifecycle context.
 *
 * @param <T> the service value type
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface StartContext<T> extends LifecycleContext {

    /**
     * Marking service completion with a value. This method returns without blocking.
     *
     * @param result the result of the service, or {@code null} if the execution type is {@link Void}
     */
    void complete(T result);

    /**
     * Marking completion of this service start failed.  This method returns without blocking.
     */
    void fail();

    /**
     * Get a service context which may be used to add child services. Child services have an implicit dependency on
     * their parent, and are automatically removed when the parent service stops (or if the parent service fails
     * during startup).
     *
     * @return the child context
     */
    ServiceContext getChildContext();

}
