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

import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc.service.ServiceMode;

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
     * @return {@code true} if the services were actually enabled, {@code false} if they were already enabled
     */
    abstract boolean doEnable(final Transaction transaction, final TaskFactory taskFactory);

    /**
     * Disables the services managed by this object.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     * @return {@code true} if the services were actually disabled, {@code false} if they were already disabled
     */
    abstract boolean doDisable(final Transaction transaction, final TaskFactory taskFactory);

    /**
     * Management operation for disabling one or more services. As a result, the affected services will stop if they are
     * {@code UP}.
     */
    public void disable(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        // retrieve enable task if available
        final Map<ServiceManager, TaskController<Void>>[] tasks = transaction.getAttachment(MANAGEMENT_TASKS);
        final TaskControllerImpl<Void> enableTask;
        synchronized (this) {
            enableTask = (TaskControllerImpl<Void>) tasks[ENABLE].remove(this);
            // if there is no enable task to cancel, and if there is not a disable task previously created for this txn
            // create a disable task
            if (enableTask == null && !tasks[DISABLE].containsKey(this)){
                final DisableServiceTask task = new DisableServiceTask(transaction);
                final TaskController<Void> taskController = transaction.getTaskFactory().newTask(task).setRevertible(task).release();
                if (tasks[DISABLE] == Collections.EMPTY_MAP) {
                    tasks[DISABLE] = new ConcurrentHashMap<>();
                }
                tasks[DISABLE].put(this, taskController);
            }
        }
        if (enableTask != null) {
            enableTask.forceCancel();
        }
    }

    /**
     * Management operation for enabling one or more services. The affected services may start as a result, according to
     * their {@link ServiceMode mode} rules.
     * <p> Services are enabled by default.
     */
    public void enable(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        final Map<ServiceManager, TaskController<Void>>[] tasks = transaction.getAttachment(MANAGEMENT_TASKS);
        // retrieve disable task if available
        final TaskControllerImpl<Void> disableTask;
        synchronized (this) {
            disableTask = (TaskControllerImpl<Void>) tasks[DISABLE].remove(this);
            // if there is no disable task to cancel, and if there is not a enable task previously created for this txn
            // create a enable task
            if (disableTask == null && !tasks[ENABLE].containsKey(this)) {
                final EnableServiceTask task = new EnableServiceTask(transaction);
                final TaskController<Void> taskController = transaction.getTaskFactory().newTask(task).setRevertible(task).release();
                if (tasks[ENABLE] == Collections.EMPTY_MAP) {
                    tasks[ENABLE] = new ConcurrentHashMap<>();
                }
                tasks[ENABLE].put(this, taskController);
            }
        }
        if (disableTask != null) {
            disableTask.forceCancel();
        }
    }

    private class EnableServiceTask implements Executable<Void>, Revertible {

        private final Transaction transaction;
        private boolean enabled;

        public EnableServiceTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                enabled = doEnable(transaction, (TaskFactory) context);
            } finally {
                context.complete();
            }
        }

        @Override
        public synchronized void rollback(RollbackContext context) {
            try {
                if (enabled) {
                    doDisable(transaction, null);
                }
            } finally {
                context.complete();
            }
        }
    }

    private class DisableServiceTask implements Executable<Void>, Revertible {

        private final Transaction transaction;
        private boolean disabled;

        public DisableServiceTask(final Transaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public synchronized void execute(ExecuteContext<Void> context) {
            try {
                disabled = doDisable(transaction, (TaskFactory) context);
            } finally {
                context.complete();
            }
        }

        @Override
        public synchronized void rollback(RollbackContext context) {
            try {
                if (disabled) {
                    doEnable(transaction, null);
                }
            } finally {
                context.complete();
            }
        }
    }
}

