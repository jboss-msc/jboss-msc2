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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.Problem.Severity;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

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
            return new ConcurrentHashMap<>();
        }

    });

    /**
     * Creates a start service task.
     * 
     * @param serviceController  starting service
     * @param dependencyStartTasks the tasks that start service dependencies (these must be first concluded before service can start)
     * @param transaction          the active transaction
     * @param taskFactory          the task factory
     * @return                     the start task (can be used for creating tasks that depend on the conclusion of
     *                              starting transition)
     */
    static <T> TaskController<T> create(final ServiceControllerImpl<T> serviceController,
            final Collection<TaskController<?>> dependencyStartTasks, final Transaction transaction, final TaskFactory taskFactory) {

        // revert starting services, i.e., service that have not been started because start task has been cancelled
        final TaskBuilderImpl<Void> tb = (TaskBuilderImpl<Void>) taskFactory.<Void>newTask(null);
        final TaskController<Void> revertStartTask = tb.
                setRevertible(new RevertStartingServiceTask(transaction, serviceController)).release();

        // start service task builder
        final TaskBuilder<T> startTaskBuilder = taskFactory.newTask(new StartServiceTask<>(serviceController, transaction));
        startTaskBuilder.addDependencies(dependencyStartTasks);
        startTaskBuilder.addDependency(revertStartTask);

        // start service
        final TaskController<T> start = startTaskBuilder.release();
        transaction.getAttachment(START_TASKS).put(serviceController, revertStartTask);

        return start;
    }

    /**
     * Attempt to revert start task for {@code service}, thus causing the service to stop if it has been started.
     * 
     * @param serviceController the service whose start task will be reverted
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

    private boolean taskValid;

    /**
     * Perform the task.
     *
     * @param context context
     */
    @Override
    public void execute(final ExecuteContext<T> context) {
        final boolean locked = serviceController.lock();
        if (!locked) return;
        int currentState = serviceController.getState();
        taskValid = currentState == ServiceControllerImpl.STATE_STARTING || currentState == ServiceControllerImpl.STATE_STOPPING || currentState == ServiceControllerImpl.STATE_RESTARTING;
        if (!taskValid) {
            context.complete();
            return;
        }
        final Service<T> service = serviceController.getService();
        if (service == null ){
            serviceController.setServiceUp(null);
            serviceController.unlock();
            context.complete(null);
            return;
        }
        service.start(new StartContext<T>() {
            @Override
            public void complete(T result) {
                serviceController.setServiceUp(result);
                serviceController.notifyServiceUp(transaction);
                serviceController.unlock();
                context.complete(result);
            }

            @Override
            public void complete() {
                serviceController.setServiceUp(null);
                serviceController.notifyServiceUp(transaction);
                serviceController.unlock();
                context.complete();
            }

            @Override
            public void fail() {
                serviceController.setServiceFailed(transaction, context);
                serviceController.notifyServiceFailed(transaction, context);
                serviceController.unlock();
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
                return ((ParentServiceContext<?>) parentContext).addService(valueType, registry,  name, (BasicUpdateTransaction)transaction, context);
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
                return ((ParentServiceContext<?>) parentContext).addService(registry,  name, (BasicUpdateTransaction)transaction, context);
            }
        });
    }

    @Override
    public void rollback(final RollbackContext context) {
        if (!taskValid) {
            context.complete();
            return;
        }
        final Service<T> service = serviceController.getService();
        if (service == null ){
            serviceController.setServiceDown(transaction, context);
            serviceController.notifyServiceDown(transaction);
            context.complete();
            return;
        }
        service.stop(new StopContext() {
            @Override
            public void complete(Void result) {
                serviceController.setServiceDown(transaction, context);
                serviceController.notifyServiceDown(transaction);
                context.complete();
            }

            @Override
            public void complete() {
                serviceController.setServiceDown(transaction, context);
                serviceController.notifyServiceDown(transaction);
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
                if (serviceController.revertStarting(transaction, context)) {
                    serviceController.notifyServiceDown(transaction);
                }
            } finally {
                context.complete();
            }
        }
    }
}
