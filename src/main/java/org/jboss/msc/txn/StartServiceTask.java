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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.Problem.Severity;

/**
 * Task that starts service.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class StartServiceTask<T> implements Executable<T>, Revertible {

    private static final AttachmentKey<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>> START_TASKS = AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>> () {

        @Override
        public ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>> create() {
            return new ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>();
        }

    });

    /**
     * Creates a start service task.
     * 
     * @param serviceController  starting service
     * @param taskDependency     the task that must be first concluded before service can start
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the start task (can be used for creating tasks that depend on the conclusion of
     *                           starting transition)
     */
    static <T> TaskController<T> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<?>> dependencyStartTasks, TaskController<?> taskDependency, Transaction transaction, TaskFactory taskFactory) {

        // revert starting services, i.e., service that have not been started because start task has been cancelled
        final TaskController<Void> revertStartTask = taskFactory.<Void>newTask().
                setRevertible(new RevertStartingServiceTask(transaction, serviceController)).release();

        // start service task builder
        final TaskBuilder<T> startTaskBuilder = taskFactory.newTask(new StartServiceTask<T>(serviceController, transaction));
        if (taskDependency != null) {
            startTaskBuilder.addDependency(taskDependency);
        }
        startTaskBuilder.addDependencies(dependencyStartTasks);
        startTaskBuilder.addDependency(revertStartTask);

        // start service
        final TaskController<T> start = startTaskBuilder.release();
        transaction.getAttachment(START_TASKS).put(serviceController, revertStartTask);

        // notify service is starting
        serviceController.notifyServiceStarting(transaction, taskFactory, start);

        return start;
    }

    /**
     * Creates a start service task.
     * 
     * @param serviceController  starting service
     * @param taskDependency     the tasks that must be first concluded before service can start
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the start task (can be used for creating tasks that depend on the conclusion of
     *                           starting transition)
     */
    static <T> TaskController<T> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<?>> dependencyStartTasks, Transaction transaction, TaskFactory taskFactory) {

        return create(serviceController, dependencyStartTasks, (TaskController<Void>) null, transaction, taskFactory);
    }

    /**
     * Attempt to revert start task for {@code service}, thus causing the service to stop if it has been started.
     * 
     * @param service     the service whose start task will be reverted
     * @param transaction the active transaction
     * @return {@code true} if a start task has been reverted; {@code false} if no such start task exists, indicating
     *                      a stop task has to be created to stop the service 
     */
    static boolean revert(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        final TaskController<?> startTask = transaction.getAttachment(START_TASKS).remove(serviceController);
        if (startTask != null) {
            ((TaskControllerImpl<?>) startTask).forceCancel();
            return true;
        }
        return false;
    }

    private final ServiceControllerImpl<T> serviceController;
    private final Transaction transaction;

    private StartServiceTask(final ServiceControllerImpl<T> serviceController, final Transaction transaction) {
        this.serviceController = serviceController;
        this.transaction = transaction;
    }

    /**
     * Perform the task.
     *
     * @param context
     */
    @Override
    public void execute(final ExecuteContext<T> context) {
        serviceController.getService().start(new StartContext<T>() {
            @Override
            public void complete(T result) {
                serviceController.setServiceUp(result, transaction);
                context.complete(result);
            }

            @Override
            public void complete() {
                serviceController.setServiceUp(null, transaction);
                context.complete();
            }

            @Override
            public void fail() {
                serviceController.setServiceFailed(transaction);
                serviceController.notifyServiceFailed(transaction, context);
                context.complete();
            }

            @Override
            public void addProblem(Problem reason) {
                context.addProblem(reason);
            }

            @Override
            public void addProblem(Severity severity, String message) {
                context.addProblem(severity, message);
            }

            @Override
            public void addProblem(Severity severity, String message, Throwable cause) {
                context.addProblem(severity, message, cause);
            }

            @Override
            public void addProblem(String message, Throwable cause) {
                context.addProblem(message, cause);
            }

            @Override
            public void addProblem(String message) {
                context.addProblem(message);
            }

            @Override
            public void addProblem(Throwable cause) {
                context.addProblem(cause);
            }

            @Override
            public <S> ServiceBuilder<S> addService(Class<S> valueType, ServiceRegistry registry, ServiceName name,
                    ServiceContext parentContext) {
                return ((ParentServiceContext<?>) parentContext).addService(valueType, registry,  name, transaction, context);
            }

            @Override
            public ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, ServiceContext parentContext) {
                return ((ParentServiceContext<?>) parentContext).addService(registry,  name, transaction, context);
            }
        });
    }

    @Override
    public void rollback(final RollbackContext context) {
        serviceController.getService().stop(new StopContext() {
            @Override
            public void complete(Void result) {
                serviceController.setServiceDown(transaction);
                serviceController.notifyServiceDown(transaction);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceDown(transaction);
                serviceController.notifyServiceDown(transaction);
                context.complete();
            }

            @Override
            public void addProblem(Problem reason) {
                context.addProblem(reason);
            }

            @Override
            public void addProblem(Severity severity, String message) {
                context.addProblem(severity, message);
            }

            @Override
            public void addProblem(Severity severity, String message, Throwable cause) {
                context.addProblem(severity, message, cause);
            }

            @Override
            public void addProblem(String message, Throwable cause) {
                context.addProblem(message, cause);
            }

            @Override
            public void addProblem(String message) {
                context.addProblem(message);
            }

            @Override
            public void addProblem(Throwable cause) {
                context.addProblem(cause);
            }
        });
    }

    /**
     * Revertible task, whose goal is to revert starting services back to DOWN state on rollback.
     */
    private static class RevertStartingServiceTask implements Revertible {

        private final Transaction transaction;
        private final ServiceControllerImpl<?> serviceController;

        public RevertStartingServiceTask(Transaction transaction, ServiceControllerImpl<?> serviceController) {
            this.transaction = transaction;
            this.serviceController = serviceController;
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                // revert only services that have not started
                if (serviceController.revertStarting(transaction)) {
                    serviceController.notifyServiceDown(transaction);
                }
            } finally {
                context.complete();
            }
        }
    }

}
