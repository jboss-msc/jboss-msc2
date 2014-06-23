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

package org.jboss.msc.test.utils;

import org.jboss.msc.txn.AbortResult;
import org.jboss.msc.txn.CommitResult;
import org.jboss.msc.txn.CompletionListener;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.Listener;
import org.jboss.msc.txn.PrepareResult;
import org.jboss.msc.txn.Problem;
import org.jboss.msc.txn.Problem.Severity;
import org.jboss.msc.txn.Revertible;
import org.jboss.msc.txn.RollbackResult;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.Transaction;
import org.jboss.msc.txn.TransactionController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.After;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test base providing some utility methods.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public abstract class AbstractTransactionTest {

    protected static final TransactionController txnController = TransactionController.createInstance();
    protected ThreadPoolExecutor defaultExecutor;

    private List<Transaction> createdTransactions = new ArrayList<Transaction>();

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

    protected static <T> TaskController<T> newTask(final Transaction transaction, final Executable<T> e,
            final Revertible r, final TaskController<?>... dependencies) {
        return txnController.newTask(transaction, e).addDependencies(dependencies).setRevertible(r).release();
    }

    protected static <T> TaskController<T> newTask(final ExecuteContext<?> ctx, final Executable<T> e, final Revertible r,
            final TaskController<?>... dependencies) {
        return ctx.newTask(e).addDependencies(dependencies).setRevertible(r).release();
    }

    protected static void prepare(Transaction transaction, Listener<PrepareResult<? extends Transaction>> listener) {
        txnController.prepare(transaction, listener);
    }

    protected static boolean canCommit(Transaction transaction) {
        return txnController.canCommit(transaction);
    }

    protected static void commit(Transaction transaction, Listener<CommitResult<? extends Transaction>> listener) {
        txnController.commit(transaction, listener);
    }

    protected static void abort(Transaction transaction, Listener<AbortResult<? extends Transaction>> listener) {
        txnController.abort(transaction, listener);
    }

    protected static void rollback(Transaction transaction, Listener<RollbackResult<? extends Transaction>> listener) {
        txnController.rollback(transaction, listener);
    }

    protected static boolean attemptToCommit(final Transaction txn) {
        prepare(txn);
        if (txnController.canCommit(txn)) {
            commit(txn);
            return true;
        } else {
            abort(txn);
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

    protected static void assertCalled(final TestTask task) {
        assertNotNull(task);
        assertTrue("Task " + task + " was not called", task.wasCalled());
    }

    protected static void assertNotCalled(final TestTask task) {
        assertNotNull(task);
        assertFalse("Task " + task + " was called", task.wasCalled());
    }

    protected static void assertCallOrder(final TestTask firstTask, final TestTask secondTask) {
        assertCalled(firstTask);
        assertCalled(secondTask);
        assertTrue("Task " + firstTask + " have been called after " + secondTask,
                firstTask.getCallTime() <= secondTask.getCallTime());
    }

    protected static void assertCallOrder(final TestTask firstTask, final TestTask secondTask, final TestTask... otherTasks) {
        assertCallOrder(firstTask, secondTask);
        if (otherTasks != null && otherTasks.length > 0) {
            TestTask previousTask = secondTask;
            for (final TestTask currentTask : otherTasks) {
                assertCallOrder(previousTask, currentTask);
                previousTask = currentTask;
            }
        }
    }

    protected static void assertPrepared(final Transaction transaction) {
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
        try {
            txnController.rollback(transaction, null);
            fail("Cannot call rollback() on prepared transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
    }

    protected static void assertAborted(final Transaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.prepare(transaction, null);
            fail("Cannot call prepare() on aborted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.commit(transaction, null);
            fail("Cannot call commit() on aborted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.abort(transaction, null);
            fail("Cannot call abort() on aborted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.rollback(transaction, null);
            fail("Cannot call rollback() on aborted transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        assertTrue(transaction.isTerminated());
    }

    protected static void assertRolledBack(final Transaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.prepare(transaction, null);
            fail("Cannot call prepare() on rolled back transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.commit(transaction, null);
            fail("Cannot call commit() on rolled back transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.abort(transaction, null);
            fail("Cannot call abort() on rolled back transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.rollback(transaction, null);
            fail("Cannot call rollback() on rolled back transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        assertTrue(transaction.isTerminated());
    }

    protected static void assertCommitted(final Transaction transaction) {
        assertNotNull(transaction);
        try {
            txnController.canCommit(transaction);
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.prepare(transaction, null);
            fail("Cannot call prepare() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.commit(transaction, null);
            fail("Cannot call commit() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.abort(transaction, null);
            fail("Cannot call abort() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        try {
            txnController.rollback(transaction, null);
            fail("Cannot call rollback() on committed transaction");
        } catch (final InvalidTransactionStateException expected) {
        }
        assertTrue(transaction.isTerminated());
    }

    protected static void prepare(final Transaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<PrepareResult<? extends Transaction>> prepareListener = new CompletionListener<>();
        txnController.prepare(transaction, prepareListener);
        prepareListener.awaitCompletionUninterruptibly();
        assertPrepared(transaction);
    }

    protected static void commit(final Transaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<CommitResult<? extends Transaction>> commitListener = new CompletionListener<>();
        txnController.commit(transaction, commitListener);
        commitListener.awaitCompletionUninterruptibly();
        assertNoCriticalProblem(transaction);
        assertCommitted(transaction);
    }

    private static void assertNoCriticalProblem(final Transaction txn) {
        List<Problem> problems = txnController.getReport(txn).getProblems();
        for (final Problem problem : problems) {
            if (problem.getSeverity() == Severity.CRITICAL) {
                if (problem.getCause() != null) {
                    problem.getCause().printStackTrace();
                }
                fail("Critical problem detected: " + problem.getMessage());
            }
        }
    }

    protected static void rollback(final Transaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        txnController.rollback(transaction, rollbackListener);
        rollbackListener.awaitCompletionUninterruptibly();
        assertRolledBack(transaction);
    }

    protected static void abort(final Transaction transaction) {
        assertNotNull(transaction);
        final CompletionListener<AbortResult<? extends Transaction>> abortListener = new CompletionListener<>();
        txnController.abort(transaction, abortListener);
        abortListener.awaitCompletionUninterruptibly();
        assertAborted(transaction);
    }

    protected static void prepareAndRollbackFromListener(final Transaction transaction) {
        assertNotNull(transaction);
        final AbortingListener transactionListener = new AbortingListener(txnController);
        txnController.prepare(transaction, transactionListener);
        transactionListener.awaitAbort();
        assertAborted(transaction);
    }

    protected static void prepareAndCommitFromListener(final Transaction transaction) {
        assertNotNull(transaction);
        final CommittingListener transactionListener = new CommittingListener(txnController);
        txnController.prepare(transaction, transactionListener);
        transactionListener.awaitCommit();
        assertCommitted(transaction);
    }

}
