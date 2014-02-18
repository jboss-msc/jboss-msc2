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
import org.jboss.msc.service.SimpleService;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.Problem.Severity;

/**
 * Tasks executed when a service is stopping.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class StoppingServiceTasks {

    private static final AttachmentKey<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> STOP_TASKS = AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> () {

        @Override
        public ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>> create() {
            return new ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>();
        }

    });

    /**
     * Creates stopping service tasks. When all created tasks finish execution, {@code service} will enter {@code DOWN} state.
     * 
     * @param serviceController  stopping service
     * @param dependentStopTasks the tasks that must be first concluded before service can stop
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the final task to be executed. Can be used for creating tasks that depend on the
     *                           conclusion of stopping transition.
     */
    @SuppressWarnings("unchecked")
    static <T> TaskController<Void> create(ServiceControllerImpl<T> serviceController, Collection<TaskController<?>> dependentStopTasks,
            Transaction transaction, TaskFactory taskFactory) {

        final Object service = serviceController.getService();

        // revert stopping services, i.e., service that have not been stopped because stop has been cancelled
        final TaskController<Void> revertStoppingTask = taskFactory.<Void>newTask()
                .setRevertible(new RevertStoppingServiceTask(serviceController, transaction)).release();

        // stop service
        final TaskBuilder<Void> stopTaskBuilder = taskFactory.<Void>newTask();
        final StopTask stopServiceTask;
        if (service instanceof SimpleService) {
            stopServiceTask = new StopSimpleServiceTask<T>(serviceController, (SimpleService<T>) service, transaction);
            stopTaskBuilder.setExecutable(stopServiceTask);
            stopTaskBuilder.setRevertible(stopServiceTask);
        } else {
            stopServiceTask = new StopServiceTask<T>(serviceController, service, transaction, serviceController.getValue());
            if (service instanceof ServiceStopExecutable) {
                stopTaskBuilder.setExecutable (stopServiceTask);
            }
            if (service instanceof ServiceStopRevertible) {
                stopTaskBuilder.setRevertible(stopServiceTask);
            }
        }
        stopTaskBuilder.addDependency(revertStoppingTask).addDependencies(dependentStopTasks);
        final TaskController<Void> stop = stopTaskBuilder.release();

        // revertStoppingTask is the one that needs to be cancelled if service has to revert stop
        transaction.getAttachment(STOP_TASKS).put(serviceController, revertStoppingTask);

        return stop;
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
        final StopFailedServiceTask stopFailedService = new StopFailedServiceTask(service, transaction);
        final TaskController<Void> revertStopFailed = taskFactory.<Void>newTask().setRevertible(stopFailedService).release();
        return taskFactory.<Void>newTask().setExecutable(stopFailedService).addDependency(revertStopFailed).release();
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

    private static StopContext createStopContext(final ServiceControllerImpl<?> serviceController, final Transaction transaction, final ExecuteContext<Void> context) {
        return new StopContext() {
            @Override
            public void complete(Void result) {
                serviceController.setServiceDown(transaction);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceDown(transaction);
                context.complete();
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
        };
    }

    private static <T> StartContext<T> createStartContext(final ServiceControllerImpl<T> serviceController, final Transaction transaction, final RollbackContext context) {
        return new StartContext<T>(){

            @Override
            public void complete(T result) {
                serviceController.setServiceUp(result, transaction, null);
                serviceController.notifyServiceUp(transaction);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceUp(null, transaction, null);
                serviceController.notifyServiceUp(transaction);
                context.complete();
            }

            @Override
            public void fail() {
                serviceController.setServiceFailed(transaction);
                serviceController.notifyServiceFailed(transaction, null);
                context.complete();
            }

            @Override
            public boolean isCancelRequested() {
                return false;
            }

            @Override
            public void cancelled() {
                // do nothing
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
        };
    }

    /**
     * Revertible task, whose goal is to revert stopping services back to UP state on rollback.
     *
     */
    static class RevertStoppingServiceTask implements Revertible {

        private final ServiceControllerImpl<?> serviceController;
        private final Transaction transaction;

        public RevertStoppingServiceTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
            this.serviceController = serviceController;
            this.transaction = transaction;
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                // revert only stopping services
                if (serviceController.revertStopping(transaction)) {
                    serviceController.notifyServiceUp(transaction);
                }
            } finally {
                context.complete();
            }
        }
    }

    private static interface StopTask extends Executable<Void>, Revertible{};

    /**
     * Task that stops service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    private static class StopSimpleServiceTask<T> implements StopTask {

        private final ServiceControllerImpl<T> serviceController;
        private final SimpleService<T> service;
        private final Transaction transaction;

        StopSimpleServiceTask(final ServiceControllerImpl<T> serviceController, final SimpleService<T> service, final Transaction transaction) {
            this.serviceController = serviceController;
            this.service = service;
            this.transaction = transaction;
        }

        public void execute(final ExecuteContext<Void> context) {
            service.stop(createStopContext(serviceController, transaction, context));
        }

        @Override
        public void rollback(final RollbackContext context) {
            service.start(createStartContext(serviceController, transaction, context));
        }
    }

    /**
     * Task that stops service.
     * 
     * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     */
    private static class StopServiceTask<T> implements StopTask {

        private final Transaction transaction;
        private final Object service;
        private final ServiceControllerImpl<T> serviceController;
        private final T serviceValue;

        StopServiceTask(final ServiceControllerImpl<T> serviceController, final Object service, final Transaction transaction, T serviceValue) {
            this.serviceController = serviceController;
            this.service = service;
            this.serviceValue = serviceValue;
            this.transaction = transaction;
        }

        public void execute(final ExecuteContext<Void> context) {
            ((ServiceStopExecutable)service).executeStop(createStopContext(serviceController, transaction, context));
        }

        @SuppressWarnings("unchecked")
        @Override
        public void rollback(final RollbackContext context) {
            ((ServiceStopRevertible<T>) service).rollbackStop(createStartContext(serviceController, transaction, context));
        }
    }

    /**
     * Sets service at DOWN state, uninjects service value and notifies dependencies, if any.
     *
     * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
     *
     */
    private static class StopFailedServiceTask implements Executable<Void>, Revertible {

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
}
