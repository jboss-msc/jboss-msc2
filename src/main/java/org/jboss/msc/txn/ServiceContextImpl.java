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

import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc._private.MSCLogger.TXN;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.txn.Problem.Severity;

/**
 * ServiceContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
class ServiceContextImpl implements ServiceContext {

    private final TransactionController transactionController;

    public ServiceContextImpl(TransactionController transactionController) {
        this.transactionController = transactionController;
    }

    @Override
    public <T> ServiceBuilder<T> addService(final Class<T> valueType, final ServiceRegistry registry, final ServiceName name, final Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        
        return new ServiceBuilderImpl<T>(transactionController, (ServiceRegistryImpl) registry, name, transaction);
    }

    @Override
    public void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        if (name == null) {
            throw SERVICE.methodParameterIsNull("name");
        }
        final Registration registration = ((ServiceRegistryImpl) registry).getRegistration(name);
        if (registration == null) {
            return;
        }
        final ServiceControllerImpl<?> controller = registration.getController();
        if (controller == null) {
            return;
        }
        controller.remove(transaction, transaction.getTaskFactory());
    }

    @Override
    public ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        validateRegistry(registry);
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        return new ServiceBuilderImpl<Void>(transactionController, (ServiceRegistryImpl) registry, name, transaction);
    }

    @Override
    public <T> ServiceBuilder<T> replaceService(Class<T> valueType, ServiceController service, Transaction transaction) {
        // TODO implement
        throw new RuntimeException("not implemented");
    }

    @Override
    public ServiceBuilder<Void> replaceService(ServiceRegistry registry, ServiceController service, Transaction transaction) {
        // TODO implement
        throw new RuntimeException("not implemented");
    }


    @Override
    public ReportableContext getReportableContext(Transaction transaction) {
        validateTransaction(transaction);
        final ProblemReport problemReport = transaction.getProblemReport();
        return new ReportableContext() {

            @Override
            public void addProblem(Problem reason) {
                problemReport.addProblem(reason);
            }

            @Override
            public void addProblem(Severity severity, String message) {
                problemReport.addProblem(new Problem(null, message, severity));
            }

            @Override
            public void addProblem(Severity severity, String message, Throwable cause) {
                problemReport.addProblem(new Problem(null,  message, cause, severity));
            }

            @Override
            public void addProblem(String message, Throwable cause) {
                problemReport.addProblem(new Problem(null, message, cause));
            }

            @Override
            public void addProblem(String message) {
                problemReport.addProblem(new Problem(null, message));
            }

            @Override
            public void addProblem(Throwable cause) {
                problemReport.addProblem(new Problem(null, cause));
            }
        };
    }

    /**
     * Validates {@code transaction} is a {@code TransactionImpl} created by the same transaction
     * controller that is associated with this context.
     * <p>
     * This method must be invoked by all methods in the subclass that use a transaction to control one or more 
     * tasks.
     * 
     * @param transaction the transaction to be validated
     * @throws IllegalArgumentException if {@code transaction} does not belong to the same transaction controller
     *                                  that created this context
     */
    protected void validateTransaction(Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        //if (!(transaction instanceof BasicTransaction) && !(transaction instanceof XATransaction)) {
        //    throw TXN.methodParameterIsInvalid("transaction");
        //} TODO should we test this?
        if (!transactionController.owns(transaction)) {
            // cannot be used by this context
            throw new IllegalArgumentException("Transaction does not belong to this context (transaction was created by a different transaction controller)");
        }
    }

    private void validateRegistry(ServiceRegistry registry) {
        if (registry == null) {
            throw TXN.methodParameterIsNull("registry");
        }
        if (!(registry instanceof ServiceRegistryImpl)) {
            throw TXN.methodParameterIsInvalid("registry");
        }
    }
}
