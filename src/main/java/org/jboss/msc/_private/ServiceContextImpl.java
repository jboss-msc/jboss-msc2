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
package org.jboss.msc._private;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.txn.Problem;
import org.jboss.msc.txn.Problem.Severity;
import org.jboss.msc.txn.ProblemReport;
import org.jboss.msc.txn.ReportableContext;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.Transaction;
import org.jboss.msc.txn.TransactionController;

/**
 * ServiceContext implementation.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class ServiceContextImpl extends TransactionControllerContext implements ServiceContext {

    public ServiceContextImpl(TransactionController transactionController) {
        super(transactionController);
    }

    @Override
    public <T> ServiceBuilder<T> addService(final Class<T> valueType, final ServiceRegistry registry, final ServiceName name, final Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        if (registry == null) {
            throw new IllegalArgumentException("registry is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (transaction == null) {
            throw new IllegalArgumentException("transaction is null");
        }
        return new ServiceBuilderImpl<T>(transactionController, (ServiceRegistryImpl) registry, name, (TransactionImpl) transaction);
    }

    @Override
    public ServiceBuilder<Void> addService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        if (registry == null) {
            throw new IllegalArgumentException("registry is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        if (transaction == null) {
            throw new IllegalArgumentException("transaction is null");
        }
        return new ServiceBuilderImpl<Void>(transactionController, (ServiceRegistryImpl) registry, name, (TransactionImpl) transaction);
    }

    @Override
    public void removeService(ServiceRegistry registry, ServiceName name, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        if (registry == null) {
            throw new IllegalArgumentException("registry is null");
        }
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        final Registration registration = ((ServiceRegistryImpl) registry).getRegistration(name);
        if (registration == null) {
            return;
        }
        final ServiceController<?> controller = registration.getController();
        if (controller == null) {
            return;
        }
        controller.remove((TransactionImpl) transaction, (TransactionImpl) transaction);
    }

    @Override
    public void removeRegistry(ServiceRegistry registry, Transaction transaction) {
        validateTransaction(transaction);
        assert registry instanceof ServiceRegistryImpl;
        ((ServiceRegistryImpl)registry).remove((TransactionImpl) transaction);
    }

    @Override
    public ReportableContext getReportableContext(Transaction transaction) {
        validateTransaction(transaction);
        final ProblemReport problemReport = ((TransactionImpl) transaction).getProblemReport();
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

}
