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

/**
 * Listener that commits the transaction. It provides utility method {@link #awaitCommit()} to wait until transaction have been
 * committed.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class CommittingListener implements Listener<PrepareResult<? extends Transaction>> {

    private final TestTransactionController transactionController;
    private final CompletionListener<CommitResult<? extends Transaction>> listener = new CompletionListener<>();

    public CommittingListener(TestTransactionController transactionController) {
        this.transactionController = transactionController;
    }

    @Override
    public void handleEvent(final PrepareResult<? extends Transaction> subject) {
        transactionController.commit(subject.getTransaction(), listener);
    }

    public void awaitCommit() {
        while (true) {
            try {
                listener.awaitCompletion();
                break;
            } catch (Exception ignored) {
            }
        }
    }

}
