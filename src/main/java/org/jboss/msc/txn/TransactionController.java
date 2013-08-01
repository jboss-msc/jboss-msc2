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

import org.jboss.msc.service.ManagementContext;


/**
 * A transaction controller, creates transactions and manages them.
 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public final class TransactionController extends SimpleAttachable {

    private static final RuntimePermission TXN_CONTROLLER_CREATE_PERM = new RuntimePermission("canCreateTransactionController");

    private final ManagementContext managementContext = new ManagementContextImpl(this);
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



    <T extends Transaction> T registerTransaction(final T transaction) {
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
    public Executor getExecutor(final Transaction transaction) throws SecurityException {
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
    public ProblemReport getProblemReport(final Transaction transaction) throws SecurityException {
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
     * Returns the management context, for managing (i.e., disabling and enabling) services and registries.
     * 
     * @return the management context
     */
    public ManagementContext getManagementContext() {
        return managementContext;
    }

    /**
     * Adds a task with an executable component to {@code transaction}.  If the task implements any of the supplementary
     * interfaces {@link Revertible}, {@link Validatable}, or {@link Committable}, the corresponding
     * builder properties will be pre-initialized.
     *
     * @param transaction the transaction
     * @param task        the task
     * @return the subtask builder
     * @throws IllegalStateException if the transaction is not open
     * @throws SecurityException if transaction was not created by this controller
     */
    public <T> TaskBuilder<T> newTask(final Transaction transaction, final Executable<T> task) throws IllegalStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.newTask(task);
    }

    /**
     * Adds a task without an executable component to {@code transaction}.  All task components will be uninitialized.
     *
     * @param transaction the transaction
     * @return the subtask builder
     * @throws IllegalStateException if the transaction is not open
     * @throws SecurityException if transaction was not created by this controller
     */
    public TaskBuilder<Void> newTask(final Transaction transaction) throws IllegalStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.newTask();
    }

    /**
     * Prepare {@code transaction}.  It is an error to prepare a transaction with unreleased tasks.
     * Once this method returns, either {@link #commit(BasicTransaction, Listener)} or {@link #rollback(BasicTransaction, Listener)} must be called.
     * After calling this method (regardless of its outcome), the transaction can not be directly modified before termination.
     *
     *
     * @param transaction        the transaction to be prepared
     * @param completionListener the listener to call when the prepare is complete or has failed
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if the transaction has already been prepared or committed
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void prepare(final BasicTransaction transaction, final Listener<? super BasicTransaction> completionListener) throws TransactionRolledBackException, InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.prepare((Listener<? super Transaction>) completionListener);
    }

    /**
     * Commit the work done by {@link #prepare(BasicTransaction, Listener)} and terminate {@code transaction}.
     *
     * @param transaction        the transaction to be committed
     * @param completionListener the listener to call when the rollback is complete
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if the transaction has already been committed or has not yet been prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void commit(final BasicTransaction transaction, final Listener<? super BasicTransaction> completionListener) throws InvalidTransactionStateException, TransactionRolledBackException, SecurityException {
        validateTransaction(transaction);
        transaction.commit((Listener<? super Transaction>) completionListener);
    }

    /**
     * Roll back {@code transaction}, undoing all work executed up until this time.
     *
     * @param transaction        the transaction to be rolled back
     * @param completionListener the listener to call when the rollback is complete
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if commit has already been initiated
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void rollback(final BasicTransaction transaction, final Listener<? super BasicTransaction> completionListener) throws TransactionRolledBackException, InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        transaction.rollback((Listener<? super Transaction>) completionListener);
    }

    /**
     * Determine whether a prepared transaction can be committed.  If it cannot, it must be rolled back.
     *
     * @param transaction the transaction
     * @return {@code true} if the transaction can be committed, {@code false} if it must be rolled back
     * @throws InvalidTransactionStateException if the transaction is not prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    public boolean canCommit(final Transaction transaction) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        return transaction.canCommit();
    }

    /**
     * Indicate that the current operation on {@code transaction} depends on the completion of the given {@code other}
     * transaction.
     * 
     * @param transaction transaction containing current operation
     * @param other the other transaction
     * @throws InterruptedException if the wait was interrupted
     * @throws DeadlockException if this wait has caused a deadlock and this task was selected to break it
     * @throws SecurityException if transaction was not created by this controller
     */
    public void waitFor(final Transaction transaction, final Transaction other) throws InterruptedException, DeadlockException, SecurityException {
        validateTransaction(transaction);
        assert other instanceof Transaction;
        transaction.waitFor(other);
    }

    /**
     * Determines whether the specified transaction have been created by this controller.
     * @param transaction to be checked
     * @return <code>true</code> if {@code transaction} have been created by this controller, <code>false</code> otherwise
     */
    public boolean owns(final Transaction transaction) {
        return transaction instanceof Transaction && this == transaction.getController();
    }

    void validateTransaction(final Transaction transaction) throws SecurityException {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        if (!owns(transaction)) {
            throw new SecurityException("Transaction not created by this controller");
        }
    }
}
