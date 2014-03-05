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
 * Service removal task.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class RemoveServiceTask implements Executable<Void>, Revertible {

    /**
     * Creates a remove service task.
     * 
     * @param serviceController  service that is being removed
     * @param stopTask           the task that must be first concluded before service can remove
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the remove task (can be used for creating tasks that depend on the conclusion of removal)
     */
    static <T> TaskController<Void> create(ServiceControllerImpl<T> serviceController, TaskController<?> stopTask,
            Transaction transaction, TaskFactory taskFactory) {
        final TaskBuilder<Void> removeTaskBuilder = taskFactory.newTask(new RemoveServiceTask(serviceController, transaction));
        if (stopTask != null) {
            removeTaskBuilder.addDependency(stopTask);
        }
        return removeTaskBuilder.release();
    }

    private final Transaction transaction;
    private final ServiceControllerImpl<?> serviceController;

    private RemoveServiceTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        this.transaction = transaction;
        this.serviceController = serviceController;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        assert context instanceof TaskFactory;
        try {
            serviceController.setServiceRemoved(transaction, context);
        } finally {
            context.complete();
        }
    }

    @Override
    public void rollback(RollbackContext context) {
        try {
            serviceController.reinstall(transaction);
        } finally {
            context.complete();
        }
    }
}
