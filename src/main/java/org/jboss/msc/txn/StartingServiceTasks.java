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

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc.service.ServiceStartExecutable;
import org.jboss.msc.service.ServiceStartRevertible;
import org.jboss.msc.service.SimpleService;
import org.jboss.msc.service.SimpleStartContext;
import org.jboss.msc.service.SimpleStopContext;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.txn.Problem.Severity;

/**
 * Tasks executed when a service is starting.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class StartingServiceTasks {

    private static final AttachmentKey<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>> START_TASKS = AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>> () {

        @Override
        public ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>> create() {
            return new ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<?>>();
        }

    });

    /**
     * Creates starting service tasks. When all created tasks finish execution, {@code service} will enter {@code UP}
     * state.
     * 
     * @param serviceController  starting service
     * @param taskDependency     the task that must be first concluded before service can start
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the final task to be executed. Can be used for creating tasks that depend on the
     *                           conclusion of starting transition.
     */
    static <T> TaskController<T> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<?>> dependencyStartTasks, TaskController<?> taskDependency, Transaction transaction, TaskFactory taskFactory) {

        // revert starting services, i.e., service that have not been started because start task has been cancelled
        final TaskController<Void> revertStartTask = taskFactory.<Void>newTask().
                setRevertible(new RevertStartingServiceTask(transaction, serviceController)).release();
        
        // start service task builder
        final Object service = serviceController.getService();
        final TaskBuilder<T> startTaskBuilder = taskFactory.<T>newTask();
        final StartTask<T> startServiceTask;
        if (service instanceof SimpleService) {
            startServiceTask = new StartSimpleServiceTask<T>(serviceController, transaction);
            startTaskBuilder.setExecutable(startServiceTask);
            startTaskBuilder.setRevertible(startServiceTask);
        } else {
            startServiceTask = new StartServiceTask<T>(serviceController, transaction);
            if (service instanceof ServiceStartExecutable) {
                startTaskBuilder.setExecutable(startServiceTask);
            }
            if (service instanceof ServiceStartRevertible) {
                startTaskBuilder.setRevertible(startServiceTask);
            }
        }
        if (taskDependency != null) {
            startTaskBuilder.addDependency(taskDependency);
        }
        startTaskBuilder.addDependencies(dependencyStartTasks);
        startTaskBuilder.addDependency(revertStartTask);

        // start service
        final TaskController<T> start = startTaskBuilder.release();
        transaction.getAttachment(START_TASKS).put(serviceController, revertStartTask);
        serviceController.notifyServiceStarting(transaction, taskFactory, start);

        return start;
    }

    /**
     * Creates starting service tasks. When all created tasks finish execution, {@code service} will enter {@code UP}
     * state.
     * 
     * @param serviceController  starting service
     * @param taskDependency     the tasks that must be first concluded before service can start
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the final task to be executed. Can be used for creating tasks that depend on the
     *                           conclusion of starting transition.
     */
    static <T> TaskController<T> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<?>> dependencyStartTasks, Transaction transaction, TaskFactory taskFactory) {

        return create(serviceController, dependencyStartTasks, (TaskController<Void>) null, transaction, taskFactory);
    }

    /**
     * Attempt to revert start tasks for {@code service}.
     * 
     * @param service     the service whose start tasks will be reverted
     * @param transaction the active transaction
     * @return {@code true} if {@code service} has start tasks created during current transaction, indicating they have
     *                      been reverted; {@code false} if no such tasks exist, indicating stop tasks have to be
     *                      created to stop the service 
     */
    static boolean revertStart(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        final TaskController<?> startTask = transaction.getAttachment(START_TASKS).remove(serviceController);
        if (startTask != null) {
            ((TaskControllerImpl<?>) startTask).forceCancel();
            return true;
        }
        return false;
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

    /**
     * Task that starts service.
     */
    static interface StartTask<T> extends Executable<T>, Revertible{}

    /**
     * Task that starts service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    static class StartSimpleServiceTask<T> implements StartTask<T> {

        private final ServiceControllerImpl<T> serviceController;
        protected final SimpleService<T> service;
        private final Transaction transaction;

        @SuppressWarnings("unchecked")
        StartSimpleServiceTask(final ServiceControllerImpl<T> serviceController, final Transaction transaction) {
            this.serviceController = serviceController;
            this.service = (SimpleService<T>) serviceController.getService();
            this.transaction = transaction;
        }

        /**
         * Perform the task.
         *
         * @param context
         */
        @Override
        public void execute(final ExecuteContext<T> context) {
            service.start(new SimpleStartContext<T>() {
                @Override
                public void complete(T result) {
                    serviceController.setServiceUp(result, transaction, (TaskFactory) context);
                    context.complete(result);
                }

                @Override
                public void complete() {
                    serviceController.setServiceUp(null, transaction, (TaskFactory) context);
                    context.complete();
                }

                @Override
                public void fail() {
                    serviceController.setServiceFailed(transaction);
                    serviceController.notifyServiceFailed(transaction, (TaskFactory) context);
                    context.complete();
                }
            });
        }

        @Override
        public void rollback(final RollbackContext context) {
            service.stop(new SimpleStopContext() {
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
            });
        }
    }

    /**
     * Task that starts service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    static class StartServiceTask<T> implements StartTask<T> {

        private final ServiceControllerImpl<T> serviceController;
        protected final Object service;
        private final Transaction transaction;
        private boolean cancelled;

        StartServiceTask(final ServiceControllerImpl<T> serviceController, final Transaction transaction/*, final Collection<TaskController<Boolean>> dependencyStartTasks*/) {
            this.serviceController = serviceController;
            this.service = serviceController.getService();
            this.transaction = transaction;
        }

        /**
         * Perform the task.
         *
         * @param context
         */
        @SuppressWarnings("unchecked")
        @Override
        public void execute(final ExecuteContext<T> context) {
            ((ServiceStartExecutable<T>) service).executeStart(new StartContext<T>() {

                @Override
                public void complete(T result) {
                    serviceController.setServiceUp(result, transaction, (TaskFactory) context);
                    context.complete(result);
                }

                @Override
                public void complete() {
                    serviceController.setServiceUp(null, transaction, (TaskFactory) context);
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
                public boolean isCancelRequested() {
                    return context.isCancelRequested();
                }

                @Override
                public void cancelled() {
                    context.cancelled();
                }

                @Override
                public <K> TaskBuilder<K> newTask(Executable<K> task) throws IllegalStateException {
                    return context.newTask(task);
                }

                @Override
                public TaskBuilder<Void> newTask() throws IllegalStateException {
                    return context.newTask();
                }

                @Override
                public void fail() {
                    serviceController.setServiceFailed(transaction);
                    serviceController.notifyServiceFailed(transaction, (TaskFactory) context);
                    context.complete();
                }
            });
        }

        @Override
        public void rollback(RollbackContext context) {
            if (cancelled) {
                context.complete();
            } else {
                serviceController.setServiceDown(transaction);
                serviceController.notifyServiceDown(transaction);
                ((ServiceStartRevertible) service).rollbackStart(context);
            }
        }
    }

}
