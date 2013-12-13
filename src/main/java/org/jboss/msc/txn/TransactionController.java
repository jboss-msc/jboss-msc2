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
package org.jboss.msc.txn;

import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.concurrent.Executor;

import org.jboss.msc.service.ServiceContext;

/**
 * A transaction controller, creates transactions and manages them.
 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public final class TransactionController extends SimpleAttachable {

    private static final RuntimePermission TXN_CONTROLLER_CREATE_PERM = new RuntimePermission("canCreateTransactionController");

    private final ServiceContext serviceContext = new ServiceContextImpl(this);

    private TransactionController() {}

    public static TransactionController createInstance() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(TXN_CONTROLLER_CREATE_PERM);
        }
        return new TransactionController();
    }

    /**
     * Create a new task transaction.
     *
     * @param executor the executor to use to run tasks
     * @return the transaction
     */
    public BasicTransaction create(final Executor executor) {
        return create(executor, Problem.Severity.WARNING);
    }

    /**
     * Create a new task transaction.
     *
     * @param executor the executor to use to run tasks
     * @param maxSeverity the maximum severity to allow
     * @return the transaction
     */
    public BasicTransaction create(final Executor executor, final Problem.Severity maxSeverity) {
        if (executor == null) {
            throw TXN.methodParameterIsNull("executor");
        }
        if (maxSeverity == null) {
            throw TXN.methodParameterIsNull("maxSeverity");
        }
        if (maxSeverity.compareTo(Problem.Severity.CRITICAL) >= 0) {
            throw TXN.illegalSeverity("maxSeverity");
        }
        return registerTransaction(new BasicTransaction(this, executor, maxSeverity));
    }

    BasicTransaction registerTransaction(final BasicTransaction transaction) {
        try {
            Transactions.register(transaction);
        } catch (final IllegalStateException e) {
            transaction.forceStateRolledBack();
            throw e;
        }
        return transaction;
    }

    /**
     * Get the transaction executor.
     * 
     * @param transaction the transaction
     * @return the transaction executor
     * @throws SecurityException if transaction was not created by this controller
     */
    public Executor getExecutor(final BasicTransaction transaction) throws SecurityException {
        validateTransaction(transaction);
        return transaction.getExecutor();
    }

    /**
     * Get the transaction problem report.
     * 
     * @param transaction the transaction
     * @return the transaction report
     * @throws SecurityException if transaction was not created by this controller
     */
    public ProblemReport getProblemReport(final BasicTransaction transaction) throws SecurityException {
        validateTransaction(transaction);
        return transaction.getProblemReport();
    }

    /**
     * Returns the service context, for creating and removing services.
     * 
     * @return the service context
     */
    public ServiceContext getServiceContext() {
        return serviceContext;
    }
    
    /**
     * Creates a new transaction aware lock. Only one transaction at a time can own the lock.
     * @return new transaction aware lock
     */
    public TransactionalLock newTransactionalLock() {
        return new TransactionalLock();
    }

    /**
     * Adds a task with an executable component to {@code transaction}.  If the task implements any of the supplementary
     * interfaces {@link Revertible} or {@link Validatable}, the corresponding builder properties will be pre-initialized.
     *
     * @param transaction the transaction
     * @param task        the task
     * @return the subtask builder
     * @throws IllegalStateException if the transaction is not open
     * @throws SecurityException if transaction was not created by this controller
     */
    public <T> TaskBuilder<T> newTask(final BasicTransaction transaction, final Executable<T> task) throws IllegalStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.getTaskFactory().newTask(task);
    }

    /**
     * Adds a task without an executable component to {@code transaction}.  All task components will be uninitialized.
     *
     * @param transaction the transaction
     * @return the subtask builder
     * @throws IllegalStateException if the transaction is not open
     * @throws SecurityException if transaction was not created by this controller
     */
    public TaskBuilder<Void> newTask(final BasicTransaction transaction) throws IllegalStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.getTaskFactory().newTask();
    }

    /**
     * Prepare {@code transaction}.  It is an error to prepare a transaction with unreleased tasks.
     * Once this method returns, either {@link #commit(BasicTransaction, Listener)} or {@link #abort(BasicTransaction, Listener)} must be called.
     * After calling this method (regardless of its outcome), the transaction can not be directly modified before termination.
     *
     *
     * @param transaction        the transaction to be prepared
     * @param completionListener the listener to call when the prepare is complete or has failed
     * @throws InvalidTransactionStateException if the transaction has already been rolled back, prepared or committed
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void prepare(final BasicTransaction transaction, final Listener<? super PrepareResult<BasicTransaction>> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.prepare((Listener<? super PrepareResult<? extends Transaction>>)completionListener);
    }

    /**
     * Commit the work done by {@link #prepare(BasicTransaction, Listener)} and terminate {@code transaction}.
     *
     * @param transaction        the transaction to be committed
     * @param completionListener the listener to call when the commit is complete
     * @throws InvalidTransactionStateException if the transaction has already been committed or has not yet been prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void commit(final BasicTransaction transaction, final Listener<? super CommitResult<BasicTransaction>> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.commit((Listener<? super CommitResult<? extends Transaction>>)completionListener);
    }

    /**
     * Abort {@code transaction}, undoing all work executed up until this time.
     * Abort can be called only after transaction's prepare request.
     *
     * @param transaction        the transaction to be aborted
     * @param completionListener the listener to call when the abort is complete
     * @throws InvalidTransactionStateException if the transaction has already been aborted or if commit has already been initiated
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void abort(final BasicTransaction transaction, final Listener<? super AbortResult<BasicTransaction>> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.abort((Listener<? super AbortResult<? extends Transaction>>)completionListener);
    }

    /**
     * Rollback {@code transaction}, undoing all work executed up until this time.
     * Rollback can be called only before transaction's prepare request.
     *
     * @param transaction        the transaction to be rolled back
     * @param completionListener the listener to call when the roll back is complete
     * @throws InvalidTransactionStateException if prepare has already been initiated
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void rollback(final BasicTransaction transaction, final Listener<? super RollbackResult<BasicTransaction>> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.rollback((Listener<? super RollbackResult<? extends Transaction>>)completionListener);
    }

    /**
     * Determine whether a prepared transaction can be committed.  If it cannot, it must be aborted.
     *
     * @param transaction the transaction
     * @return {@code true} if the transaction can be committed, {@code false} if it must be aborted
     * @throws InvalidTransactionStateException if the transaction is not prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    public boolean canCommit(final BasicTransaction transaction) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.canCommit();
    }

    /**
     * Indicate that the current operation on {@code transaction} depends on the completion of the given {@code other}
     * transaction.
     * 
     * @param transaction transaction containing current operation
     * @param other the other transaction
     * @throws DeadlockException if this wait would cause a deadlock
     * @throws SecurityException if transaction was not created by this controller
     */
    public void waitFor(final BasicTransaction transaction, final BasicTransaction other) throws TransactionDeadlockException, SecurityException {
        validateTransaction(transaction);
        validateTransaction(other);
        Transactions.waitFor(transaction, other);
    }

    /**
     * Determines whether the specified transaction have been created by this controller.
     * @param transaction to be checked
     * @return <code>true</code> if {@code transaction} have been created by this controller, <code>false</code> otherwise
     */
    public boolean owns(final Transaction transaction) {
        return this == transaction.getController();
    }

    void validateTransaction(final BasicTransaction transaction) throws SecurityException {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        if (!owns(transaction)) {
            throw new SecurityException("Transaction not created by this controller");
        }
    }
}
