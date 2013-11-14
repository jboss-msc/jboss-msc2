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


/**
 * Task for undemanding dependencies.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
class UndemandDependenciesTask implements Executable<Void> {

    private Transaction transaction;
    private ServiceControllerImpl<?> service;

    /**
     * Creates and releases the undemand dependencies task.
     * 
     * @param service      the service whose dependencies will be undemanded by the task
     * @param transaction  the active transaction
     * @param taskFactory  the task factory
     * @return the task controller
     */
    static TaskController<Void> create(ServiceControllerImpl<?> service, Transaction transaction, TaskFactory taskFactory) {
        return create(service, null, transaction, taskFactory);
    }

    /**
     * Creates and releases the undemand dependencies task.
     * <p>
     * Invoke this method when the undemand dependencies task must be executed only after other tasks finish execution.
     * 
     * @param service          the service whose dependencies will be undemanded by the task
     * @param taskDependencies the dependencies of the undemand dependencies task
     * @param transaction      the active transaction
     * @param taskFactory      the task factory
     * @return the task controller
     */
    static TaskController<Void> create(ServiceControllerImpl<?> service, TaskController<?> taskDependency, Transaction transaction, TaskFactory taskFactory) {
        if (service.getDependencies().length == 0) {
            return null;
        }
        TaskBuilder<Void> taskBuilder = taskFactory.newTask(new UndemandDependenciesTask(transaction, service));
        if (taskDependency != null) {
            taskBuilder.addDependencies(taskDependency);
        }
        return taskBuilder.release();
    }

    private UndemandDependenciesTask(Transaction transaction, ServiceControllerImpl<?> service) {
        this.transaction = transaction;
        this.service = service;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        assert context instanceof TaskFactory;
        try {
            for (DependencyImpl<?> dependency: service.getDependencies()) {
                dependency.undemand(transaction, (TaskFactory)context);
            }
        } finally {
            context.complete();
        }
    }
}
