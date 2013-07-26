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

import static org.jboss.msc._private.ServiceController.STATE_REMOVED;

import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;

/**
 * Service removal task.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class ServiceRemoveTask implements Executable<Void> {
    private final TransactionImpl transaction;
    private final ServiceController<?> serviceController;

    ServiceRemoveTask(ServiceController<?> serviceController, TransactionImpl transaction) {
        this.transaction = transaction;
        this.serviceController = serviceController;
    }

    @Override
    public void execute(ExecuteContext<Void> context) {
        try {
            serviceController.getPrimaryRegistration().clearController(transaction, context);
            for (Registration registration: serviceController.getAliasRegistrations()) {
                registration.clearController(transaction, context);
            }
            serviceController.setTransition(STATE_REMOVED, transaction, context);
        } finally {
            context.complete();
        }
    }
}
