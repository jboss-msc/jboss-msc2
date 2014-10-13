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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static org.jboss.msc._private.MSCLogger.TXN;

/**
 * A builder for subtasks.  Subtasks may be configured with dependencies and injections before being installed.
 * Dependency tasks must be associated with the same transaction as the subtask being built, or a parent thereof.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TaskBuilderImpl<T> implements TaskBuilder<T> {

    @SuppressWarnings("rawtypes")
    private final AbstractTransaction txn;
    private ClassLoader classLoader;
    private Executable<T> executable;

    TaskBuilderImpl(final AbstractTransaction txn, final Executable<T> executable) {
        this.txn = txn;
        this.executable = executable;
    }

    @Override
    public Transaction getTransaction() {
        return txn.wrappingTxn;
    }

    @Override
    public TaskBuilderImpl<T> setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
        return this;
    }

    @Override
    public TaskControllerImpl<T> release() {
        @SuppressWarnings("rawtypes")
        final TaskControllerImpl<T> controller = new TaskControllerImpl<>(txn, executable, classLoader);
        controller.install();
        return controller;
    }
}
