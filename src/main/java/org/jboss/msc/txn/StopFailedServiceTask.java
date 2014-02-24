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
 * Task that stops a failed service.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class StopFailedServiceTask implements Executable<Void>, Revertible {

    /**
     * Creates stop failed service task.
     * 
     * @param service         failed service that is stopping
     * @param transaction     the active transaction
     * @param taskFactory      the task factory
     * @return                the stop task (can be used for creating tasks that depend on the conclusion of stopping
     *                        transition)
     */
    static <T> TaskController<Void> create(ServiceControllerImpl<T> service, Transaction transaction, TaskFactory taskFactory) {
        // post stop task
        final StopFailedServiceTask stopFailedService = new StopFailedServiceTask(service, transaction);
        final TaskController<Void> revertStopFailed = taskFactory.<Void>newTask().setRevertible(stopFailedService).release();
        return taskFactory.<Void>newTask().setExecutable(stopFailedService).addDependency(revertStopFailed).release();
    }

    private final Transaction transaction;
    private final ServiceControllerImpl<?> serviceController;

    private StopFailedServiceTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        this.transaction = transaction;
        this.serviceController = serviceController;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        assert context instanceof TaskFactory;
        try {
            serviceController.setServiceDown(transaction);
        } finally {
            context.complete();
        }
    }

    @Override
    public void rollback(RollbackContext context) {
        try {
            serviceController.setServiceFailed(transaction);
        } finally {
            context.complete();
        }
    }
}
