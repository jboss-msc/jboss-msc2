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

import java.util.Collection;

/**
 * A builder for subtasks.  Subtasks may be configured with dependencies and injections before being installed.
 * Dependency tasks must be associated with the same transaction as the subtask being built, or a parent thereof.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface TaskBuilder<T> {

    /**
     * Get the transaction associated with this builder.
     *
     * @return the transaction associated with this builder
     */
    Transaction getTransaction();

    /**
     * Change the executable part of this task, or {@code null} to prevent the executable part from running.
     *
     * @param executable the new executable part
     * @return this task builder
     */
    TaskBuilder<T> setExecutable(final Executable<T> executable);

    /**
     * Set the class loader to use for this task.
     *
     * @param classLoader the class loader
     * @return this task builder
     */
    TaskBuilder<T> setClassLoader(final ClassLoader classLoader);

    /**
     * Add dependencies, if this subtask has not yet been executed.
     *
     * @param dependencies the dependencies to add
     * @return this builder
     */
    TaskBuilder<T> addDependencies(final TaskController<?>... dependencies) throws IllegalStateException;

    /**
     * Add dependencies, if this subtask has not yet been executed.
     *
     * @param dependencies the dependencies to add
     * @return this builder
     */
    TaskBuilder<T> addDependencies(final Collection<? extends TaskController<?>> dependencies) throws IllegalStateException;

    /**
     * Add a single dependency, if this subtask has not yet been executed.
     *
     * @param dependency the dependency to add
     * @return this builder
     */
    TaskBuilder<T> addDependency(final TaskController<?> dependency) throws IllegalStateException;

    /**
     * Release this task to begin execution.  The given listener is called upon completion or failure, or immediately
     * if this task was already released.
     *
     * @return the new controller
     */
    TaskController<T> release();
}
