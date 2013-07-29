/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

import com.arjuna.ats.arjuna.AtomicAction;

/**
 * A transaction controller's resource for interacting with ArjunaCore.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ArjunaResource {
    private final TransactionController transactionController;

    private static final AttachmentKey<ArjunaResource> KEY = AttachmentKey.create();
    private static final ArjunaTransactionManagementScheme SCHEME = new ArjunaTransactionManagementScheme();

    ArjunaResource(final TransactionController transactionController) {
        this.transactionController = transactionController;
    }

    /**
     * Get the Arjuna resource for the given transaction controller.
     *
     * @return the Arjuna resource for the given transaction controller
     */
    public static ArjunaResource getArjunaResource(TransactionController controller) {
        ArjunaResource resource = controller.getAttachmentIfPresent(KEY);
        if (resource == null) {
            resource = new ArjunaResource(controller);
            ArjunaResource appearing = controller.putAttachmentIfAbsent(KEY, resource);
            if (appearing != null) {
                resource = appearing;
            }
        }
        return resource;
    }

    /**
     * Get the transaction controller.
     *
     * @return the transaction controller
     */
    public TransactionController getTransactionController() {
        return transactionController;
    }

    /**
     * Register a transaction with the given atomic action.
     *
     * @param action the atomic action
     * @param transaction the transaction to register
     */
    public void registerWithAction(AtomicAction action, Transaction transaction) {
        TransactionManagementScheme scheme = transaction.getAttachmentIfPresent(TransactionManagementScheme.KEY);
        if (scheme == null) {
            scheme = transaction.putAttachmentIfAbsent(TransactionManagementScheme.KEY, SCHEME);
            if (scheme == null) {
                scheme = SCHEME;
            }
        }
        if (scheme != SCHEME) {
            throw new IllegalStateException("Transaction is already associated with another transaction manager");
        }
        // todo - verify transaction against transactionController
        action.add(new ArjunaRecordImplementation(this, transaction));
    }

}
