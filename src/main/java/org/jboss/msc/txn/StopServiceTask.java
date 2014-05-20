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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.Problem.Severity;

/**
 * Task that stops service.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class StopServiceTask<T> implements Executable<Void>, Revertible {

    private static final AttachmentKey<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> STOP_TASKS = AttachmentKey.create(new Factory<ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>> () {

        @Override
        public ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>> create() {
            return new ConcurrentHashMap<ServiceControllerImpl<?>, TaskController<Void>>();
        }

    });

    /**
     * Creates a stop service task.
     * 
     * @param serviceController  stopping service
     * @param dependentStopTasks the tasks that must be first concluded before service can stop
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the stop task (can be used for creating tasks that depend on the conclusion of stopping
     *                           transition)
     */
    static <T> TaskController<Void> create(ServiceControllerImpl<T> serviceController, Collection<TaskController<?>> dependentStopTasks,
            Transaction transaction, TaskFactory taskFactory) {

        return create(serviceController, null, dependentStopTasks, transaction, taskFactory);
    }

    /**
     * Creates a stop service task.
     * 
     * @param serviceController  stopping service
     * @param taskDependency     a task that must be first concluded before service can stop
     * @param dependentStopTasks the tasks that must be first concluded before service can stop
     * @param transaction        the active transaction
     * @param taskFactory        the task factory
     * @return                   the stop task (can be used for creating tasks that depend on the conclusion of stopping
     *                           transition)
     */
    static <T> TaskController<Void> create(ServiceControllerImpl<T> serviceController, TaskController<?> taskDependency,
            Collection<TaskController<?>> dependentStopTasks, Transaction transaction, TaskFactory taskFactory) {

        // revert stopping services, i.e., service that have not been stopped because stop has been cancelled
        final TaskController<Void> revertStoppingTask = taskFactory.<Void>newTask()
                .setRevertible(new RevertStoppingServiceTask(serviceController, transaction)).release();

        // stop service
        final TaskBuilder<Void> stopTaskBuilder = taskFactory.newTask(new StopServiceTask<T>(serviceController, transaction))
                .addDependency(revertStoppingTask).addDependencies(dependentStopTasks);
        if (taskDependency != null) {
            stopTaskBuilder.addDependency(taskDependency);
        }
        final TaskController<Void> stop = stopTaskBuilder.release();

        // revertStoppingTask is the one that needs to be cancelled if service has to revert stop
        transaction.getAttachment(STOP_TASKS).put(serviceController, revertStoppingTask);

        return stop;
    }

    /**
     * Attempt to revert stop task for {@code service}, thus causing a service to restart if it has been stopped.
     * 
     * @param service     the service whose stop tasks will be reverted
     * @param transaction the active transaction
     * @return {@code true} if a stop task has been reverted; {@code false} if no such stop task exists, indicating
     *                      a start task has to be created to start the service 
     */
    static boolean revert(ServiceControllerImpl<?> service, Transaction transaction) {
        final TaskController<Void> stopTask = transaction.getAttachment(STOP_TASKS).remove(service);
        if (stopTask != null) {
            ((TaskControllerImpl<Void>) stopTask).forceCancel();
            return true;
        }
        return false;
    }

    private final ServiceControllerImpl<T> serviceController;
    private final Transaction transaction;

    private StopServiceTask(final ServiceControllerImpl<T> serviceController, final Transaction transaction) {
        this.serviceController = serviceController;
        this.transaction = transaction;
    }

    public void execute(final ExecuteContext<Void> context) {
        final Service<T> service = serviceController.getService();
        if (service == null) {
            serviceController.setServiceDown(transaction, context);
            context.complete();
            return;
        }
        service.stop(new StopContext() {
            @Override
            public void complete(Void result) {
                serviceController.setServiceDown(transaction, context);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceDown(transaction, context);
                context.complete();
            }

            @Override
            public void addProblem(Problem reason) {
                if (reason == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("reason");
                }
                context.addProblem(reason);
            }

            @Override
            public void addProblem(Severity severity, String message) {
                if (severity == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("severity");
                }
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                context.addProblem(severity, message);
            }

            @Override
            public void addProblem(Severity severity, String message, Throwable cause) {
                if (severity == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("severity");
                }
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(severity, message, cause);
            }

            @Override
            public void addProblem(String message, Throwable cause) {
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(message, cause);
            }

            @Override
            public void addProblem(String message) {
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                context.addProblem(message);
            }

            @Override
            public void addProblem(Throwable cause) {
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(cause);
            }
        });
    }

    @Override
    public void rollback(final RollbackContext context) {
        final Service<T> service = serviceController.getService();
        if (service == null) {
            serviceController.setServiceUp(null, transaction, context);
            serviceController.notifyServiceUp(transaction);
            context.complete();
            return;
        }
        service.start(new StartContext<T>() {

            @Override
            public void complete(T result) {
                serviceController.setServiceUp(result, transaction, context);
                serviceController.notifyServiceUp(transaction);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceUp(null, transaction, context);
                serviceController.notifyServiceUp(transaction);
                context.complete();
            }

            @Override
            public void fail() {
                serviceController.setServiceFailed(transaction, context);
                serviceController.notifyServiceFailed(transaction, null);
                context.complete();
            }

            @Override
            public void addProblem(Problem reason) {
                if (reason == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("reason");
                }
                context.addProblem(reason);
            }

            @Override
            public void addProblem(Severity severity, String message) {
                if (severity == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("severity");
                }
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                context.addProblem(severity, message);
            }

            @Override
            public void addProblem(Severity severity, String message, Throwable cause) {
                if (severity == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("severity");
                }
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(severity, message, cause);
            }

            @Override
            public void addProblem(String message, Throwable cause) {
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(message, cause);
            }

            @Override
            public void addProblem(String message) {
                if (message == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("message");
                }
                context.addProblem(message);
            }

            @Override
            public void addProblem(Throwable cause) {
                if (cause == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("cause");
                }
                context.addProblem(cause);
            }

            @Override
            public <S> ServiceBuilder<S> addService(Class<S> valueType, ServiceRegistry registry, ServiceName name,
                    ServiceContext parentContext) {
                if (valueType == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("valueType");
                }
                if (registry == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("registry");
                }
                if (name == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("name");
                }
                if (parentContext == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("parentContext");
                }
                return ((ParentServiceContext<?>) parentContext).addService(valueType,  registry,  name, transaction, context);
            }

            @Override
            public ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, ServiceContext parentContext) {
                if (registry == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("registry");
                }
                if (name == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("name");
                }
                if (parentContext == null) {
                    throw MSCLogger.SERVICE.methodParameterIsNull("parentContext");
                }
                return ((ParentServiceContext<?>) parentContext).addService(registry,  name, transaction, context);
            }
        });
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
                if (serviceController.revertStopping(transaction, context)) {
                    serviceController.notifyServiceUp(transaction);
                }
            } finally {
                context.complete();
            }
        }
    }

}
