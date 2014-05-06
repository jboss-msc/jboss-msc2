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


/**
 * Service installation task.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class InstallServiceTask implements Executable<Void>, Revertible {
    private final Transaction transaction;
    private final ServiceControllerImpl<?> serviceController;
    boolean installed;

    InstallServiceTask(ServiceControllerImpl<?> serviceController, Transaction transaction) {
        this.transaction = transaction;
        this.serviceController = serviceController;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        assert context instanceof TaskFactory;
        try {
            installed = serviceController.completeInstallation(transaction, context);
        } finally {
            context.complete();
        }
    }

    @Override
    public void rollback(RollbackContext context) {
        try {
            if (installed) {
                serviceController.clear(transaction, null);
            }
        } finally {
            context.complete();
        }
    }
}
