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

import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A builder for subtasks.  Subtasks may be configured with dependencies and injections before being installed.
 * Dependency tasks must be associated with the same transaction as the subtask being built, or a parent thereof.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TaskBuilderImpl<T> implements TaskBuilder<T> {

    @SuppressWarnings("rawtypes")
    private static final TaskControllerImpl[] NO_TASKS = new TaskControllerImpl[0];
    private final Transaction transaction;
    private final TaskParent parent;
    private final Set<TaskControllerImpl<?>> dependencies = Collections.newSetFromMap(new IdentityHashMap<TaskControllerImpl<?>, Boolean>());
    private ClassLoader classLoader;
    private Executable<T> executable;
    private Revertible revertible;

    TaskBuilderImpl(final Transaction transaction, final TaskParent parent, final Executable<T> executable) {
        this.transaction = transaction;
        this.parent = parent;
        this.executable = executable;
        if (executable instanceof Revertible) revertible = (Revertible) executable;
    }

    TaskBuilderImpl(final Transaction transaction, final TaskParent parent) {
        this(transaction, parent, null);
    }

    @Override
    public Transaction getTransaction() {
        return transaction;
    }

    @Override
    public TaskBuilderImpl<T> setExecutable(final Executable<T> executable) {
        this.executable = executable;
        return this;
    }

    @Override
    public TaskBuilderImpl<T> setRevertible(final Revertible revertible) {
        if (!transaction.isActive()) {
            throw TXN.cannotAddRevertibleToInactiveTransaction();
        }
        this.revertible = revertible;
        return this;
    }

    @Override
    public TaskBuilderImpl<T> setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    @Override
    public TaskBuilderImpl<T> addDependencies(final TaskController<?>... dependencies) throws IllegalStateException {
        if (dependencies == null) {
            throw TXN.methodParameterIsNull("dependencies");
        }
        for (final TaskController<?> dependency : dependencies) {
            addDependency(dependency);
        }
        return this;
    }

    @Override
    public TaskBuilderImpl<T> addDependencies(final Collection<? extends TaskController<?>> dependencies) throws IllegalStateException {
        if (dependencies == null) {
            throw TXN.methodParameterIsNull("dependencies");
        }
        for (final TaskController<?> dependency : dependencies) {
            addDependency(dependency);
        }
        return this;
    }

    @Override
    public TaskBuilderImpl<T> addDependency(final TaskController<?> dependency) throws IllegalStateException {
        if (dependency == null) {
            throw TXN.methodParameterIsNull("dependency");
        }
        dependencies.add((TaskControllerImpl<?>) dependency);
        return this;
    }

    @Override
    public TaskControllerImpl<T> release() {
        @SuppressWarnings("rawtypes")
        final TaskControllerImpl[] dependenciesArray = dependencies.isEmpty() ? NO_TASKS : dependencies.toArray(new TaskControllerImpl[dependencies.size()]);
        final TaskControllerImpl<T> controller = new TaskControllerImpl<>(parent, dependenciesArray, executable, revertible, classLoader);
        controller.install();
        return controller;
    }
}
