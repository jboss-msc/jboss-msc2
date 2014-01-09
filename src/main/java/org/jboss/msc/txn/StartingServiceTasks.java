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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceStartExecutable;
import org.jboss.msc.service.ServiceStartRevertible;
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

    // keep track of services that have failed to start at current transaction
    static final AttachmentKey<Set<Object>> FAILED_SERVICES = AttachmentKey.<Set<Object>>create(new Factory<Set<Object>>() {
        @Override
        public Set<Object> create() {
            return new HashSet<Object>();
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
    static <T> TaskController<Boolean> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<Boolean>> dependencyStartTasks, TaskController<?> taskDependency, Transaction transaction, TaskFactory taskFactory) {

        // start service task builder
        final Object service = serviceController.getService();
        final StartServiceTask<T> startServiceTask = new StartServiceTask<T>(serviceController, transaction, dependencyStartTasks);
        final TaskBuilder<T> startBuilder = taskFactory.<T>newTask();
        if (service instanceof ServiceStartExecutable) {
            startBuilder.setExecutable(startServiceTask);
        }
        if (service instanceof ServiceStartRevertible) {
            startBuilder.setRevertible(startServiceTask);
        }

        final TaskController<T> start; 

        if (hasDependencies(serviceController)) {
            // notify dependent is starting to dependencies
            final TaskBuilder<Void> notifyDependentStartBuilder = taskFactory.newTask(new NotifyDependentStartTask(transaction, serviceController));
            if (taskDependency != null) {
                notifyDependentStartBuilder.addDependency(taskDependency);
            }
            notifyDependentStartBuilder.addDependencies(dependencyStartTasks);
            final TaskController<Void> notifyDependentStart = notifyDependentStartBuilder.release();
            startBuilder.addDependency(notifyDependentStart);
            transaction.getAttachment(START_TASKS).put(serviceController, notifyDependentStart);

            // start service
            start = startBuilder.release();

        } else {
            if (taskDependency != null) {
                startBuilder.addDependency(taskDependency);
            }

            // start service
            start = startBuilder.release();
            transaction.getAttachment(START_TASKS).put(serviceController, start);
        }

        return taskFactory.newTask(new SetServiceUpTask<T>(serviceController, start, transaction)).addDependency(start).release();
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
    static <T> TaskController<Boolean> create(ServiceControllerImpl<T> serviceController,
            Collection<TaskController<Boolean>> dependencyStartTasks, Transaction transaction, TaskFactory taskFactory) {

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

    private static boolean hasDependencies(ServiceControllerImpl<?> service) {
        return service.getDependencies().length > 0;
    }

    /**
     * Task that notifies dependencies that a dependent service is about to start
     */
    private static class NotifyDependentStartTask implements Executable<Void>, Revertible {

        private final Transaction transaction;
        private final ServiceControllerImpl<?> serviceController;

        public NotifyDependentStartTask(Transaction transaction, ServiceControllerImpl<?> serviceController) {
            this.transaction = transaction;
            this.serviceController = serviceController;
        }

        @Override
        public void execute(ExecuteContext<Void> context) {
            assert context instanceof TaskFactory;
            try {
                for (DependencyImpl<?> dependency: serviceController.getDependencies()) {
                    ServiceControllerImpl<?> dependencyController = dependency.getDependencyRegistration().getController();
                    if (dependencyController != null) {
                        dependencyController.dependentStarted(transaction);
                    }
                }
            } finally {
                context.complete();
            }
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                for (DependencyImpl<?> dependency: serviceController.getDependencies()) {
                    ServiceControllerImpl<?> dependencyController = dependency.getDependencyRegistration().getController();
                    if (dependencyController != null) {
                        dependencyController.dependentStopped(transaction);
                    }
                }
            } finally {
                context.complete();
            }
        }
    }

    /**
     * Task that starts service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    static class StartServiceTask<T> implements Executable<T>, Revertible {

        private final ServiceControllerImpl<T> serviceController;
        protected final Object service;
        private final Transaction transaction;
        private boolean failed;
        private final Collection<TaskController<Boolean>> dependencyStartTasks;

        StartServiceTask(final ServiceControllerImpl<T> serviceController, final Transaction transaction, final Collection<TaskController<Boolean>> dependencyStartTasks) {
            this.serviceController = serviceController;
            this.service = serviceController.getService();
            this.transaction = transaction;
            this.dependencyStartTasks = dependencyStartTasks;
        }

        /**
         * Perform the task.
         *
         * @param context
         */
        @SuppressWarnings("unchecked")
        @Override
        public void execute(final ExecuteContext<T> context) {
            for (TaskController<Boolean> dependencyStartTask: dependencyStartTasks) {
                if (!dependencyStartTask.getResult()) {
                    failed = true;
                    serviceController.setTransition(ServiceControllerImpl.STATE_DOWN, transaction);
                    transaction.getAttachment(START_TASKS).remove(serviceController);
                    context.cancelled();
                    return;
                }
            }
            ((ServiceStartExecutable<T>) service).executeStart(new StartContext<T>() {

                @Override
                public void complete(T result) {
                    context.complete(result);
                }

                @Override
                public void complete() {
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
                    transaction.getAttachment(StartingServiceTasks.FAILED_SERVICES).add(service);
                    complete();
                }
            });
        }

        @Override
        public void rollback(RollbackContext context) {
            if (failed) {
                context.complete();
            } else {
                ((ServiceStartRevertible) service).rollbackStart(context);
            }
        }
    }


    /**
     * Task that sets service at UP state, and performs service value injection.
     */
    private static class SetServiceUpTask<T> implements Executable<Boolean>, Revertible {

        private final ServiceControllerImpl<T> service;
        private final TaskController<T> serviceStartTask;
        private final Transaction transaction;

        private SetServiceUpTask (ServiceControllerImpl<T> service, TaskController<T> serviceStartTask, Transaction transaction) {
            this.service = service;
            this.serviceStartTask = serviceStartTask;
            this.transaction = transaction;
        }

        @Override
        public void execute(ExecuteContext<Boolean> context) {
            assert context instanceof TaskFactory;
            boolean serviceUp = false;
            try {
                T result = serviceStartTask.getResult();
                // service failed
                if (result == null && transaction.getAttachment(FAILED_SERVICES).contains(service.getService())) {
                    MSCLogger.FAIL.startFailed(service.getServiceName());
                    service.setTransition(ServiceControllerImpl.STATE_FAILED, transaction);
                } else {
                    service.setValue(result);
                    service.setTransition(ServiceControllerImpl.STATE_UP, transaction);
                    serviceUp = true;
                    service.notifyServiceUp(transaction, (TaskFactory) context);
                }
            } finally {
                context.complete(serviceUp);
            }
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                service.setValue(null);
                service.notifyServiceDown(transaction);
                service.setTransition(ServiceControllerImpl.STATE_DOWN, transaction);
            } finally {
                context.complete();
            }
        }
    }
}
