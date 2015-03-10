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

import org.jboss.msc.util.CompletionListener;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test base providing some utility methods.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractTransactionTest {

    protected static final TestTransactionController txnController = TestTransactionController.createInstance();
    protected ThreadPoolExecutor defaultExecutor;

    private List<Transaction> createdTransactions = new ArrayList<>();

    @Before
    public void setUp() {
        defaultExecutor = newExecutor(8, true);
    }

    @After
    public void tearDown() {
        try {
            for (Transaction transaction : createdTransactions) {
                assertTrue("Unterminated transaction", transaction.isTerminated());
            }
        } finally {
            createdTransactions.clear();
        }
        defaultExecutor.shutdown();
        try {
            defaultExecutor.awaitTermination(60, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        assertTrue(defaultExecutor.getQueue().size() == 0);
    }

    protected static ThreadPoolExecutor newExecutor(final int maximumPoolSize) {
        return newExecutor(maximumPoolSize, false);
    }

    protected static ThreadPoolExecutor newExecutor(final int maximumPoolSize, final boolean prestartCoreThreads) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(maximumPoolSize, maximumPoolSize, 0L, TimeUnit.DAYS,
                new LinkedBlockingQueue<Runnable>());
        if (prestartCoreThreads) {
            executor.prestartAllCoreThreads();
        }
        return executor;
    }

    protected static boolean attemptToCommit(final UpdateTransaction txn) {
        prepare(txn);
        if (txnController.canCommit(txn)) {
            commit(txn);
            return true;
        } else {
            commit(txn);
            return false;
        }
    }

    protected UpdateTransaction newUpdateTransaction() {
        assertNotNull(defaultExecutor);
        final CompletionListener<UpdateTransaction> listener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, listener);
        final UpdateTransaction transaction = listener.awaitCompletionUninterruptibly();
        createdTransactions.add(transaction);
        return transaction;
    }

    protected UpdateTransaction newUpdateTransaction(final Executor executor) {
        assertNotNull(executor);
        final CompletionListener<UpdateTransaction> listener = new CompletionListener<>();
        txnController.createUpdateTransaction(executor, listener);
        final UpdateTransaction transaction = listener.awaitCompletionUninterruptibly();
        createdTransactions.add(transaction);
        return transaction;
    }

    protected static void assertPrepared(final UpdateTransaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
        } catch (InvalidTransactionStateException e) {
            fail("It must be possible to call canCommit() on prepared transaction");
        }
        try {
            txnController.prepare(transaction, null);
            fail("Cannot call prepare() on prepared transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
    }

    protected static void assertRestarted(final UpdateTransaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
            fail("Cannot call canCommit() on restarted transaction");
        } catch (InvalidTransactionStateException expected) {
        }
        try {
            txnController.restart(transaction, null);
            fail("Cannot call restart() on restarted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.commit(transaction, null);
            fail("Cannot call commit() on restarted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
    }

    protected static void assertCommitted(final Transaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
            fail("Cannot call canCommit() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        if (transaction instanceof UpdateTransaction) {
            try {
                txnController.prepare((UpdateTransaction)transaction, null);
                fail("Cannot call prepare() on committed transaction");
            } catch (final InvalidTransactionStateException expected) {
            }
            try {
                txnController.restart((UpdateTransaction)transaction, null);
                fail("Cannot call commit() on committed transaction");
            } catch (final InvalidTransactionStateException expected) {
            }
        }
        try {
            txnController.commit(transaction, null);
            fail("Cannot call commit() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        assertTrue(transaction.isTerminated());
    }

    protected static void prepare(final UpdateTransaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<Transaction> prepareListener = new CompletionListener<>();
        txnController.prepare(transaction, prepareListener);
        prepareListener.awaitCompletionUninterruptibly();
        assertPrepared(transaction);
    }

    protected static UpdateTransaction restart(final UpdateTransaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<UpdateTransaction> restartListener = new CompletionListener<>();
        txnController.restart(transaction, restartListener);
        final UpdateTransaction retVal = restartListener.awaitCompletionUninterruptibly();
        assertRestarted(transaction);
        return retVal;
    }

    protected static void commit(final Transaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<Transaction> commitListener = new CompletionListener<>();
        txnController.commit(transaction, commitListener);
        commitListener.awaitCompletionUninterruptibly();
        assertCommitted(transaction);
    }

}
