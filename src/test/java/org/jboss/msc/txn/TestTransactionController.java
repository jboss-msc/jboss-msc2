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

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TestTransactionController {

    private final TransactionController delegate;

    private TestTransactionController(final TransactionController delegate) {
        this.delegate = delegate;
    }

    public static TestTransactionController createInstance() {
        return new TestTransactionController(TransactionController.createInstance());
    }

    public <T> TestTaskBuilder<T> newTask(final Transaction transaction, final Executable<T> task) {
        return new TestTaskBuilder(delegate.newTask(transaction, task));
    }

    public boolean canCommit(final Transaction transaction) {
        return delegate.canCommit(transaction);
    }

    public <T extends Transaction> void prepare(final T transaction, final Listener<? super PrepareResult<T>> completionListener) {
        delegate.prepare(transaction, completionListener);
    }

    public <T extends Transaction> void commit(final T transaction, final Listener<? super CommitResult<T>> completionListener) {
        delegate.commit(transaction, completionListener);
    }

    public void createUpdateTransaction(final Executor executor, final Listener<UpdateTransaction> listener) {
        delegate.createUpdateTransaction(executor, listener);
    }

    public void createReadTransaction(final Executor executor, final Listener<ReadTransaction> listener) {
        delegate.createReadTransaction(executor, listener);
    }

    public boolean downgradeTransaction(final UpdateTransaction updateTxn, final Listener<ReadTransaction> listener) {
        return delegate.downgradeTransaction(updateTxn, listener);
    }

    public boolean upgradeTransaction(final ReadTransaction readTxn, final Listener<UpdateTransaction> listener) {
        return delegate.upgradeTransaction(readTxn, listener);
    }

    public void restart(final UpdateTransaction updateTxn) {
        delegate.restart(updateTxn);
    }

    public ServiceContext getServiceContext() {
        return delegate.getServiceContext();
    }

    public ServiceContainer createServiceContainer() {
        return delegate.createServiceContainer();
    }

}