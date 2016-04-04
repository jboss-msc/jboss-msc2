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

import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.util.Listener;

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TestTransactionController {

    private final TransactionController delegate;

    private TestTransactionController(final TransactionController delegate) {
        this.delegate = delegate;
    }

    public static TestTransactionController newInstance() {
        return new TestTransactionController(TransactionController.newInstance());
    }

    public boolean canCommit(final Transaction transaction) {
        return delegate.canCommit(transaction);
    }

    public void prepare(final UpdateTransaction transaction, final Listener<? super UpdateTransaction> completionListener) {
        delegate.prepare(transaction, completionListener);
    }

    public <T extends Transaction> void commit(final T transaction, final Listener<T> completionListener) {
        delegate.commit(transaction, completionListener);
    }

    public void newUpdateTransaction(final Executor executor, final Listener<UpdateTransaction> listener) {
        delegate.newUpdateTransaction(executor, listener);
    }

    public void newReadTransaction(final Executor executor, final Listener<ReadTransaction> listener) {
        delegate.newReadTransaction(executor, listener);
    }

    public boolean downgrade(final UpdateTransaction updateTxn, final Listener<ReadTransaction> listener) {
        return delegate.downgrade(updateTxn, listener);
    }

    public void upgrade(final ReadTransaction readTxn, final Listener<UpdateTransaction> listener) {
        delegate.upgrade(readTxn, listener);
    }

    public void restart(final UpdateTransaction updateTxn, final Listener<? super UpdateTransaction> completionListener) {
        delegate.restart(updateTxn, completionListener);
    }

    public ServiceContext newServiceContext(final UpdateTransaction updateTxn) {
        return delegate.newServiceContext(updateTxn);
    }

    public ServiceContainer newServiceContainer(final UpdateTransaction updateTxn) {
        return delegate.newServiceContainer(updateTxn);
    }

}