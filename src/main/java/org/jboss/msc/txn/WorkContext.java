/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
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



/**
 * Context for a task.
 *
 * @param <T> the result type of the associated task
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface WorkContext<T> extends ReportableContext, SimpleWorkContext {

    /**
     * Register the completion of this task with a value.  This method returns without blocking.
     *
     * @param result the result of the task, or {@code null} if the execution type is {@link Void}
     */
    void complete(T result);

    /**
     * Register the completion of this task with a {@code null} value.  This method returns without blocking.
     */
    void complete();
}
