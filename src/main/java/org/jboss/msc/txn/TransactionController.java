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

import org.jboss.msc._private.ManagementContextImpl;
import org.jboss.msc._private.ServiceContextImpl;
import org.jboss.msc._private.TransactionImpl;
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
    public Transaction create(final Executor executor) {
        return TransactionImpl.createTransactionImpl(this, executor, Problem.Severity.WARNING);
    }

    /**
     * Create a new task transaction.
     *
     * @param executor the executor to use to run tasks
     * @param maxSeverity the maximum severity to allow
     * @return the transaction
     */
    public Transaction create(final Executor executor, final Problem.Severity maxSeverity) {
        if (executor == null) {
            throw TXN.methodParameterIsNull("executor");
        }
        if (maxSeverity == null) {
            throw TXN.methodParameterIsNull("maxSeverity");
        }
        if (maxSeverity.compareTo(Problem.Severity.CRITICAL) >= 0) {
            throw TXN.illegalSeverity("maxSeverity");
        }
        return TransactionImpl.createTransactionImpl(this, executor, maxSeverity);
    }

    /**
     * Get the transaction executor.
     * 
     * @param transaction the transaction
     * @return the transaction executor
     */
    public Executor getExecutor(final Transaction transaction) {
        validateTransaction(transaction);
        return ((TransactionImpl) transaction).getExecutor();
    }

    public ProblemReport getProblemReport(final Transaction transaction) {
        validateTransaction(transaction);
        return ((TransactionImpl) transaction).getProblemReport();
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
     */
    public <T> TaskBuilder<T> newTask(final Transaction transaction, final Executable<T> task) throws IllegalStateException {
        validateTransaction(transaction);
        return ((TransactionImpl) transaction).newTask(task);
    }

    /**
     * Adds a task without an executable component to {@code transaction}.  All task components will be uninitialized.
     *
     * @param transaction the transaction
     * @return the subtask builder
     * @throws IllegalStateException if the transaction is not open
     */
    public TaskBuilder<Void> newTask(final Transaction transaction) throws IllegalStateException {
        validateTransaction(transaction);
        return ((TransactionImpl) transaction).newTask();
    }

    /**
     * Prepare {@code transaction}.  It is an error to prepare a transaction with unreleased tasks.
     * Once this method returns, either {@link #commit(Transaction, Listener)} or {@link #rollback(Transaction,Listener)} must be called.
     * After calling this method (regardless of its outcome), the transaction can not be directly modified before termination.
     *
     * @param transaction        the transaction to be prepared
     * @param completionListener the listener to call when the prepare is complete or has failed
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if the transaction has already been prepared or committed
     */
    public void prepare(final Transaction transaction, final Listener<? super Transaction> completionListener) throws TransactionRolledBackException, InvalidTransactionStateException {
        validateTransaction(transaction);
        ((TransactionImpl) transaction).prepare(completionListener);
    }

    /**
     * Commit the work done by {@link #prepare(Transaction,Listener)} and terminate {@code transaction}.
     *
     * @param transaction        the transaction to be committed
     * @param completionListener the listener to call when the rollback is complete
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if the transaction has already been committed or has not yet been prepared
     */
    public void commit(final Transaction transaction, final Listener<? super Transaction> completionListener) throws InvalidTransactionStateException, TransactionRolledBackException {
        validateTransaction(transaction);
        ((TransactionImpl) transaction).commit(completionListener);
    }

    /**
     * Roll back {@code transaction}, undoing all work executed up until this time.
     *
     * @param transaction        the transaction to be rolled back
     * @param completionListener the listener to call when the rollback is complete
     * @throws TransactionRolledBackException if the transaction was previously rolled back
     * @throws InvalidTransactionStateException if commit has already been initiated
     */
    public void rollback(final Transaction transaction, final Listener<? super Transaction> completionListener) throws TransactionRolledBackException, InvalidTransactionStateException {
        validateTransaction(transaction);
        ((TransactionImpl) transaction).rollback(completionListener);
    }

    /**
     * Determine whether a prepared transaction can be committed.  If it cannot, it must be rolled back.
     *
     * @param transaction the transaction
     * @return {@code true} if the transaction can be committed, {@code false} if it must be rolled back
     * @throws InvalidTransactionStateException if the transaction is not prepared
     */
    public boolean canCommit(final Transaction transaction) throws InvalidTransactionStateException {
        validateTransaction(transaction);
        return ((TransactionImpl) transaction).canCommit();
    }

    /**
     * Indicate that the current operation on {@code transaction} depends on the completion of the given {@code other}
     * transaction.
     * 
     * @param transaction transaction containing current operation
     * @param other the other transaction
     * @throws InterruptedException if the wait was interrupted
     * @throws DeadlockException if this wait has caused a deadlock and this task was selected to break it
     */
    public void waitFor(final Transaction transaction, final Transaction other) throws InterruptedException, DeadlockException {
        validateTransaction(transaction);
        assert other instanceof TransactionImpl;
        ((TransactionImpl) transaction).waitFor(other);
    }

    /**
     * Determines whether the specified transaction have been created by this controller.
     * @param transaction to be checked
     * @return <code>true</code> if {@code transaction} have been created by this controller, <code>false</code> otherwise
     */
    public boolean owns(final Transaction transaction) {
        if (transaction == null) return false;
        if (!(transaction instanceof TransactionImpl)) return false;
        return ((TransactionImpl) transaction).getController() == this;
    }

    private void validateTransaction(final Transaction transaction) {
        assert transaction instanceof TransactionImpl;
        if (((TransactionImpl) transaction).getController() != this) {
            throw new IllegalArgumentException("Transaction not created by this ontroller");
        }
    }

}
