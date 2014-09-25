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
package org.jboss.msc.txn;

import org.jboss.msc.service.ServiceMode;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Performs disable/enable management operations over a set of one or more services.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
abstract class ServiceManager {

    private static final int ENABLE = 0;
    private static final int DISABLE = 1;

    private static final AttachmentKey<Map<ServiceManager, TaskController<Void>>[]> MANAGEMENT_TASKS = AttachmentKey.create(new Factory<Map<ServiceManager, TaskController<Void>>[]> () {

        @SuppressWarnings("unchecked")
        @Override
        public Map<ServiceManager, TaskController<Void>>[] create() {
            return new Map[] {Collections.EMPTY_MAP, Collections.EMPTY_MAP};
        }

    });

    /**
     * Enables the services managed by this object.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    abstract void doEnable(final Transaction transaction, final TaskFactory taskFactory);

    /**
     * Disables the services managed by this object.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    abstract void doDisable(final Transaction transaction, final TaskFactory taskFactory);

    /**
     * Management operation for disabling one or more services. As a result, the affected services will stop if they are
     * {@code UP}.
     */
    public void disable(final UpdateTransaction transaction) {
        // retrieve enable task if available
        final Map<ServiceManager, TaskController<Void>>[] tasks = transaction.getAttachment(MANAGEMENT_TASKS);
        final TaskControllerImpl<Void> enableTask;
        synchronized (this) {
            enableTask = (TaskControllerImpl<Void>) tasks[ENABLE].remove(this);
            // create a disable task
            if (!tasks[DISABLE].containsKey(this)){
                final DisableServiceTask task = new DisableServiceTask(transaction);
                final TaskBuilderImpl<Void> tb = (TaskBuilderImpl<Void>) getAbstractTransaction(transaction).getTaskFactory().newTask(task);
                if (enableTask != null) {
                    tb.addDependency(enableTask);
                }
                final TaskController<Void> taskController = tb.release();
                if (tasks[DISABLE] == Collections.EMPTY_MAP) {
                    tasks[DISABLE] = new ConcurrentHashMap<>();
                }
                tasks[DISABLE].put(this, taskController);
            }
        }
    }

    /**
     * Management operation for enabling one or more services. The affected services may start as a result, according to
     * their {@link ServiceMode mode} rules.
     * <p> Services are enabled by default.
     */
    public void enable(final UpdateTransaction transaction) {
        final Map<ServiceManager, TaskController<Void>>[] tasks = transaction.getAttachment(MANAGEMENT_TASKS);
        // retrieve disable task if available
        final TaskControllerImpl<Void> disableTask;
        synchronized (this) {
            disableTask = (TaskControllerImpl<Void>) tasks[DISABLE].remove(this);
            // create a enable task
            if (!tasks[ENABLE].containsKey(this)) {
                final EnableServiceTask task = new EnableServiceTask(transaction);
                final TaskBuilderImpl<Void> tb = (TaskBuilderImpl<Void>) getAbstractTransaction(transaction).getTaskFactory().newTask(task);
                if (disableTask != null) {
                    tb.addDependency(disableTask);
                }
                final TaskController<Void> taskController = tb.release();
                if (tasks[ENABLE] == Collections.EMPTY_MAP) {
                    tasks[ENABLE] = new ConcurrentHashMap<>();
                }
                tasks[ENABLE].put(this, taskController);
            }
        }
    }

    private class EnableServiceTask implements Executable<Void> {

        private final Transaction transaction;

        public EnableServiceTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                doEnable(transaction, context);
            } finally {
                context.complete();
            }
        }
    }

    private class DisableServiceTask implements Executable<Void> {

        private final Transaction transaction;

        public DisableServiceTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                doDisable(transaction, context);
            } finally {
                context.complete();
            }
        }
    }
}

