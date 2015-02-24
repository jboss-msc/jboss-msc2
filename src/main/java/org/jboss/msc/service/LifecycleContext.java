/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import java.util.concurrent.Executor;

/**
 * Service lifecycle context.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface LifecycleContext extends WorkContext, Executor {

    /**
     * Get the amount of time elapsed since the start or stop was initiated, in nanoseconds.
     *
     * @return the elapsed time
     */
    long getElapsedTime();

    /**
     * Get the associated service controller.
     *
     * @return the service controller
     */
    ServiceController<?> getController();

    /**
     * Execute a task asynchronously using the MSC task executor.
     * <p>
     * <strong>Note:</strong> This method should not be used for executing tasks that may block,
     * particularly from within a service's {@link Service#start(StartContext)} or {@link Service#stop(StopContext)}
     * methods. See {@linkplain Service the Service class javadoc} for further details.
     *
     * @param command the command to execute
     */
    @Override
    void execute(Runnable command);

}
