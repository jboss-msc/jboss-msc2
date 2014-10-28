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
import org.jboss.msc.problem.Problem;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.problem.Problem.Severity;

import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Task that starts service.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class StartServiceTask<T> implements Executable<T> {

    /**
     * Creates a start service task.
     * 
     * @param serviceController  starting service
     * @param transaction          the active transaction
     */
    static <T> void create(final ServiceControllerImpl<T> serviceController, final Transaction transaction) {
        // start service
        final TaskFactory taskFactory = getAbstractTransaction(transaction).getTaskFactory();
        taskFactory.newTask(new StartServiceTask<>(serviceController, transaction)).release();
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
     * @param context context
     */
    @Override
    public void execute(final ExecuteContext<T> context) {
        final Service<T> service = serviceController.getService();
        if (service == null ){
            serviceController.setServiceUp(null, transaction);
            context.complete(null);
            return;
        }
        service.start(new StartContext<T>() {
            @Override
            public void complete(T result) {
                serviceController.setServiceUp(result, transaction);
                serviceController.notifyServiceUp(transaction);
                context.complete(result);
            }

            @Override
            public void complete() {
                serviceController.setServiceUp(null, transaction);
                serviceController.notifyServiceUp(transaction);
                context.complete();
            }

            @Override
            public void fail() {
                serviceController.setServiceFailed(transaction);
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
                return parentContext.addService(valueType, registry,  name, (BasicUpdateTransaction) transaction);
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
                return parentContext.addService(registry,  name, (BasicUpdateTransaction) transaction);
            }
        });
    }
}
