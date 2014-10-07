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

import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Task that stops a failed service.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class StopFailedServiceTask implements Executable<Void> {

    /**
     * Creates stop failed service task.
     * 
     * @param service         failed service that is stopping
     * @param transaction     the transaction
     */
    static <T> void create(ServiceControllerImpl<T> service, Transaction transaction) {
        final TaskFactory taskFactory = getAbstractTransaction(transaction).getTaskFactory();
        taskFactory.newTask(new StopFailedServiceTask(service, transaction)).release();
    }

    private final ServiceControllerImpl<?> serviceController;
    private final Transaction transaction;

    private StopFailedServiceTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        this.serviceController = serviceController;
        this.transaction = transaction;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        try {
            serviceController.setServiceDown(transaction);
        } finally {
            context.complete();
        }
    }
}
