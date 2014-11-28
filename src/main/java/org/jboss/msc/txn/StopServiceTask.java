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

import static org.jboss.msc.txn.Helper.setTCCL;

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.problem.Problem;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.problem.Problem.Severity;

import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Task that stops service.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class StopServiceTask<T> implements Executable<Void> {

    /**
     * Creates a stop service task.
     * 
     * @param serviceController  stopping service
     * @param transaction        the active transaction
     */
    static <T> void create(ServiceControllerImpl<T> serviceController, Transaction transaction) {
        final TaskFactory taskFactory = getAbstractTransaction(transaction).getTaskFactory();
        taskFactory.newTask(new StopServiceTask<>(serviceController, transaction)).release();
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
            serviceController.setServiceDown(transaction);
            serviceController.notifyServiceDown(transaction);
            context.complete();
            return;
        }
        service.stop(new StopContext() {
            @Override
            public void complete() {
                serviceController.setServiceDown(transaction);
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

            public long getElapsedTime() {
                return System.nanoTime() - serviceController.lifecycleTime;
            }

            public ServiceController<?> getController() {
                return serviceController;
            }

            public void execute(final Runnable command) {
                final ClassLoader contextClassLoader = setTCCL(command.getClass().getClassLoader());
                try {
                    command.run();
                } finally {
                    setTCCL(contextClassLoader);
                }
            }

        });
    }

}
