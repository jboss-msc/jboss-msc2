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

import org.jboss.msc.service.ServiceStopExecutable;
import org.jboss.msc.service.ServiceStopRevertible;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.Problem.Severity;

/**
 * Tasks executed when a service is stopping.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class StoppingServiceTasks {

    static final AttachmentKey<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> STOP_TASKS = AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> () {

        @Override
        public ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>> create() {
            return new ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>();
        }

    });

    /**
     * Creates stopping service tasks. When all created tasks finish execution, {@code service} will enter {@code DOWN} state.
     * 
     * @param service          stopping service
     * @param taskDependencies the tasks that must be first concluded before service can stop
     * @param transaction      the active transaction
     * @param taskFactory      the task factory
     * @return                 the final task to be executed. Can be used for creating tasks that depend on the
     *                         conclusion of stopping transition.
     */
    static <T> TaskController<Void> create(ServiceControllerImpl<T> service, Collection<TaskController<?>> taskDependencies,
            Transaction transaction, TaskFactory taskFactory) {

        final Object serviceValue = service.getService();

        // notify dependents
        final TaskController<Void> notifyDependentsTask = taskFactory.<Void>newTask().setRevertible(new NotifyDependentsTask(service, transaction)).release();

        // stop service
        final TaskBuilder<Void> stopTaskBuilder = taskFactory.<Void>newTask();
        final StopServiceTask stopServiceTask = new StopServiceTask(serviceValue);
        if (serviceValue instanceof ServiceStopExecutable) {
            stopTaskBuilder.setExecutable (stopServiceTask);
        }
        if (serviceValue instanceof ServiceStopRevertible) {
            stopTaskBuilder.setRevertible(stopServiceTask);
        }
        stopTaskBuilder.addDependencies(taskDependencies);
        stopTaskBuilder.addDependency(notifyDependentsTask);

        final TaskController<Void> stop = stopTaskBuilder.release();

        // notifyDependentsTask is the one that needs to be reverted if service has to rollback stop
        transaction.getAttachment(STOP_TASKS).put(service, notifyDependentsTask);

        // post stop task
        final TaskBuilder<Void> setServiceDownBuilder =  taskFactory.newTask(new SetServiceDownTask<T>(service, transaction, service.getValue()));

        // undemand dependencies if needed
        if (service.getDependencies().length > 0) {
            TaskController<Void> undemandDependenciesTask = UndemandDependenciesTask.create(service, stop, transaction, taskFactory);
            setServiceDownBuilder.addDependency(undemandDependenciesTask);
        } else {
            setServiceDownBuilder.addDependency(stop);
        }

        return setServiceDownBuilder.release();
    }

    /**
     * Creates stopping service tasks. When all created tasks finish execution, {@code service} will enter {@code DOWN} state.
     * @param service         failed service that is stopping
     * @param transaction     the active transaction
     * @param taskFactory      the task factory
     * @return                the final task to be executed. Can be used for creating tasks that depend on the
     *                        conclusion of stopping transition.
     */
    static <T> TaskController<Void> createForFailedService(ServiceControllerImpl<T> service, Transaction transaction, TaskFactory taskFactory) {

        // post stop task
        final TaskBuilder<Void> setServiceDownBuilder = taskFactory.newTask(new SetServiceDownTask<T>(service, transaction, null));

        // undemand dependencies if needed
        if (service.getDependencies().length > 0) {
            TaskController<Void> undemandDependenciesTask = UndemandDependenciesTask.create(service, transaction, transaction.getTaskFactory());
            setServiceDownBuilder.addDependency(undemandDependenciesTask);
        }

        return setServiceDownBuilder.release();
    }

    /**
     * Attempt to revert stop tasks for {@code service}.
     * 
     * @param service     the service whose stop tasks will be reverted
     * @param transaction the active transaction
     * @return {@code true} if {@code service} has stop tasks created during current transaction, indicating they have
     *                      been reverted; {@code false} if no such tasks exist, indicating start tasks have to be
     *                      created to start the service 
     */
    static boolean revertStop(ServiceControllerImpl<?> service, Transaction transaction) {
        final TaskController<Void> stopTask = transaction.getAttachment(STOP_TASKS).remove(service);
        if (stopTask != null) {
            ((TaskControllerImpl<Void>) stopTask).forceCancel();
            return true;
        }
        return false;
    }

    static class NotifyDependentsTask implements Revertible {

        private final ServiceControllerImpl<?> serviceController;
        private final Transaction transaction;

        public NotifyDependentsTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
            this.serviceController = serviceController;
            this.transaction = transaction;
        }

        @Override
        public void rollback(RollbackContext context) {
            // set UP state
            serviceController.setTransition(ServiceControllerImpl.STATE_UP, transaction);
            try {
                serviceController.notifyServiceUp(transaction);
            } finally {
                context.complete();
            }
        }
    }

    /**
     * Task that stops service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    static class StopServiceTask implements Executable<Void>, Revertible {

        private final Object service;

        StopServiceTask(final Object service) {
            this.service = service;
        }

        public void execute(final ExecuteContext<Void> context) {
            ((ServiceStopExecutable)service).executeStop(new StopContext(){
                @Override
                public void lockAsynchronously(TransactionalLock lock, LockListener listener) {
                    context.lockAsynchronously(lock, listener);
                }

                @Override
                public boolean tryLock(TransactionalLock lock) {
                    return context.tryLock(lock);
                }

                @Override
                public void complete(Void result) {
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
                public <T> TaskBuilder<T> newTask(Executable<T> task) throws IllegalStateException {
                    return context.newTask(task);
                }

                @Override
                public TaskBuilder<Void> newTask() throws IllegalStateException {
                    return context.newTask();
                }
            });
        }

        @Override
        public void rollback(RollbackContext context) {
            ((ServiceStopRevertible) service).rollbackStop(context);
        }
    }

    /**
     * Sets service at DOWN state, uninjects service value and notifies dependencies, if any.
     *
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     *
     */
    private static class SetServiceDownTask<T> implements Executable<Void>, Revertible {

        private final Transaction transaction;
        private final ServiceControllerImpl<T> serviceController;
        private final T serviceValue;

        private SetServiceDownTask(ServiceControllerImpl<T> serviceController, Transaction transaction, T serviceValue) {
            this.transaction = transaction;
            this.serviceController = serviceController;
            this.serviceValue = serviceValue;
        }

        @Override
        public void execute(ExecuteContext<Void> context) {
            assert context instanceof TaskFactory;
            try {
                // set down state
                serviceController.setTransition(ServiceControllerImpl.STATE_DOWN, transaction);

                // clear service value, thus performing an automatic uninjection
                serviceController.setValue(null);

                // notify dependent is stopped
                for (DependencyImpl<?> dependency: serviceController.getDependencies()) {
                    ServiceControllerImpl<?> dependencyController = dependency.getDependencyRegistration().getController();
                    if (dependencyController != null) {
                        dependencyController.dependentStopped(transaction, (TaskFactory)context);
                    }
                }
            } finally {
                context.complete();
            }
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                // notify dependent is up
                for (DependencyImpl<?> dependency: serviceController.getDependencies()) {
                    ServiceControllerImpl<?> dependencyController = dependency.getDependencyRegistration().getController();
                    if (dependencyController != null) {
                        dependencyController.dependentStarted(transaction);
                    }
                }

                // reset service value, thus performing an automatic injection
                serviceController.setValue(serviceValue);
            } finally {
                context.complete();
            }
        }

    }
}
