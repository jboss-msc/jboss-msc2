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

package org.jboss.msc.txn;

import org.jboss.msc._private.MSCLogger;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ThreadLocalExecutor {

    private static final ThreadLocal<Deque<Runnable>> tasks = new ThreadLocal<Deque<Runnable>>() {
        @Override public Deque<Runnable> initialValue() {
            return new ArrayDeque<>();
        }
    };

    private static final ThreadLocal<Boolean> executingTasks = new ThreadLocal<Boolean>() {
        @Override public Boolean initialValue() {
            return Boolean.FALSE;
        }
    };

    static void addTask(final Runnable r) {
        tasks.get().addFirst(r);
    }

    static void executeTasks() {
        if (executingTasks.get() == Boolean.FALSE) {
            executingTasks.set(Boolean.TRUE);
            Runnable task = null;
            final Deque<Runnable> currentTasks = tasks.get();
            while (!currentTasks.isEmpty()) {
                try {
                    task = currentTasks.pollFirst();
                    task.run();
                } catch (Throwable t) {
                    MSCLogger.FAIL.runnableExecuteFailed(t, task);
                }
            }
            executingTasks.set(Boolean.FALSE);
        }
    }

}
