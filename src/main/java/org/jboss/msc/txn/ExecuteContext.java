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

package org.jboss.msc.txn;

/**
 * Context for a task that may succeed or fail which may also produce a consumable result.
 *
 * @param <T> the result type of the associated task
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface ExecuteContext<T> extends TransactionalContext, ReportableContext, CancellableContext, SimpleWorkContext {

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

    /**
     * Adds a task with an executable component to {@code transaction}.  If the task implements any of the supplementary
     * interfaces {@link Revertible}, {@link Validatable}, or {@link Committable}, the corresponding
     * builder properties will be pre-initialized.
     * 
     * @param task the task
     * @param <R> the result value type (may be {@link Void})
     * @return the builder for the task
     * @throws IllegalStateException if this context is not accepting new tasks
     */
    <R> TaskBuilder<R> newTask(Executable<R> task) throws IllegalStateException;

    /**
     * Adds a task without an executable component to {@code transaction}.  All task components will be uninitialized.
     * 
     * @return the builder for the task
     * @throws IllegalStateException if this context is not accepting new tasks
     */
    TaskBuilder<Void> newTask() throws IllegalStateException;
}
