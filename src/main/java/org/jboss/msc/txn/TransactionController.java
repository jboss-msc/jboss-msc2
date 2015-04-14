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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc._private.Version;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.util.Listener;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.Executor;

import static org.jboss.msc._private.MSCLogger.TXN;
import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Transaction controller is the main entry point to MSC.
 * The purpose of this class is to create MSC runtime
 * and to create and manage transactions operating upon it.

 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public final class TransactionController {

    private static final RuntimePermission TXN_CONTROLLER_CREATE_PERM = new RuntimePermission("canCreateTransactionController");

    // TXN administration lock
    private final Object lock = new Object();
    // whether currently running TXNs are read-only or updating. There can be only single updating TXN at a time.
    private boolean updatingTxnRunning;
    // count of running TXNs in this round
    private int runningTxns;
    // TXNs that are pending execution, each item is either single updating TXN or set of reading TXNs
    private final Deque<PendingTxnEntry> pendingTxns = new ArrayDeque<>();

    static {
        MSCLogger.ROOT.greeting(Version.getVersionString());
    }

    private TransactionController() {}

    /**
     * Factory method for creating transaction controllers.
     * @return new transaction controller instance
     * @throws SecurityException if executing code is not allowed to create transaction controller
     */
    public static TransactionController newInstance() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(TXN_CONTROLLER_CREATE_PERM);
        }
        return new TransactionController();
    }

    /**
     * Creates a new read-only transaction asynchronously.
     * The completion <B>listener</B> is called when read-only transaction is created.
     *
     * @param executor the executor to use to run tasks
     * @param listener transaction creation completion listener
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public void newReadTransaction(final Executor executor, final Listener<? super ReadTransaction> listener) throws IllegalArgumentException {
        if (executor == null) {
            throw TXN.methodParameterIsNull("executor");
        }
        if (listener == null) {
            throw TXN.methodParameterIsNull("listener");
        }
        registerReadTransaction(new BasicReadTransaction(this, executor), listener);
    }

    /**
     * Creates a new updating transaction asynchronously.
     * The completion <B>listener</B> is called when updating transaction is created.
     *
     * @param executor the executor to use to run tasks
     * @param listener transaction creation completion listener
     * @throws IllegalArgumentException if any parameter is {@code null}
     */
    public void newUpdateTransaction(final Executor executor, final Listener<? super UpdateTransaction> listener) throws IllegalArgumentException {
        if (executor == null) {
            throw TXN.methodParameterIsNull("executor");
        }
        if (listener == null) {
            throw TXN.methodParameterIsNull("listener");
        }
        registerUpdateTransaction(new BasicUpdateTransaction(new BasicReadTransaction(this, executor)), listener);
    }

    /**
     * Downgrades updating <B>updateTxn</B> transaction to read-only transaction.
     * This operation succeeds iff <B>updateTxn</B> didn't modify anything in MSC runtime.
     * If downgrade is not successful, the completion listener will never be called.
     * But once downgrade is successful (indicated by returning <B>true</B> from the method)
     * <B>updateTxn</B> is invalidated and cannot be used by user anymore.
     * User have to wait for completion <B>listener</B> to be called back to get reference to transformed
     * read-only transaction and use this new reference instead of previous <B>updateTxn</B> reference.<P/><P/>
     * Sample usage:
     * <PRE>
     *     private void executeSomeReadOnlyTasks(final ReadTransaction txn) {
     *         // ... some code using read-only txn
     *     }
     *
     *     private void foo() {
     *         UpdateTransaction updateTxn = ...// some mistakenly created updating transaction
     *         Listener&lt;ReadTransaction&gt; completionListener = new Listener&lt;&gt;() {
     *             public void handleEvent(final ReadTransaction txn) {
     *                 executeSomeReadOnlyTasks(txn);
     *             }
     *         };
     *         final boolean success = TransactionController.downgrade(updateTxn, completionListener);
     *         if (success) {
     *             // code in completion listener will execute sometime ...
     *         } else {
     *             executeSomeReadOnlyTasks(updateTxn); // UpdateTransaction also implements ReadTransaction, executing code now ...
     *         }
     *     }
     * </PRE>
     * @param updateTxn transaction to be transformed to read-only transaction
     * @param listener transaction transformation completion listener
     * @return {@code true} if downgrade was successful, {@code false} otherwise
     * @throws IllegalArgumentException if any parameter is null
     * @throws SecurityException if there's a <B>TransactionController</B> mismatch
     */
    @SuppressWarnings("unchecked")
    public boolean downgrade(final UpdateTransaction updateTxn, final Listener<? super ReadTransaction> listener) throws IllegalArgumentException, SecurityException {
        final BasicUpdateTransaction basicUpdateTxn = validateTransaction(updateTxn);
        if (listener == null) {
            throw TXN.methodParameterIsNull("listener");
        }
        // if basicUpdateTxn didn't modify anything, convert it
        BasicReadTransaction basicReadTxn;
        synchronized (basicUpdateTxn.getLock()) {
            if (basicUpdateTxn.isModified()) {
                // if transaction modified anything we cannot downgrade
                return false;
            }
            basicReadTxn = basicUpdateTxn.getDelegate();
            basicUpdateTxn.invalidate();
            basicReadTxn.setWrappingTransaction(basicReadTxn);
        }
        Deque<PendingTxnEntry> notifications = null;
        synchronized (lock) {
            assert runningTxns == 1;
            updatingTxnRunning = false;
            if (pendingTxns.size() > 0) {
                pendingTxns.addFirst(new PendingTxnEntry(basicReadTxn, (Listener<Object>)listener));
                runningTxns--;
                notifications = getNotifications();
            }
        }
        if (notifications != null) {
            for (final PendingTxnEntry notification : notifications) {
                safeCallListener(notification.listener, notification.txn);
            }
        } else {
            safeCallListener((Listener<Object>)listener, basicReadTxn);
        }
        return true;
    }

    /**
     * Upgrades read-only <B>readTxn</B> transaction to updating transaction.
     * When upgrade is finished <B>readTxn</B> remains still valid and can be used by user anytime. Further more
     * user can wait for completion <B>listener</B> to be called and obtain the reference to transformed
     * updating transaction and use this new reference to do some modification tasks.<P/><P/>
     * Sample usage:
     * <PRE>
     *     private void executeSomeModifyingTasks(final UpdateTransaction txn) {
     *         // ... some code using updating txn
     *     }
     *
     *     private void foo() {
     *         ReadTransaction readTxn = ... // some mistakenly created read-only transaction
     *         Listener&lt;UpdateTransaction&gt; completionListener = new Listener&lt;&gt;() {
     *             public void handleEvent(final UpdateTransaction txn) {
     *                 executeSomeModifyingTasks(txn);
     *             }
     *         };
     *         final boolean success = TransactionController.upgradeTransaction(readTxn, completionListener);
     *         if (success) {
     *             // code in completion listener will execute sometime ...
     *         } else {
     *             // we cannot execute modifying tasks with read-only transaction :(
     *             throw new RuntimeException();
     *         }
     *     }
     * </PRE>
     * @param readTxn transaction to be transformed to updating transaction
     * @param listener transaction transformation completion listener
     * @return {@code true} if upgrade was successful, {@code false} otherwise
     * @throws IllegalArgumentException if any parameter is null
     * @throws SecurityException if there's a <B>TransactionController</B> mismatch
     */
    @SuppressWarnings("unchecked")
    public boolean upgrade(final ReadTransaction readTxn, final Listener<? super UpdateTransaction> listener) throws IllegalArgumentException, SecurityException {
        final BasicReadTransaction basicReadTxn = validateTransaction(readTxn);
        if (listener == null) {
            throw TXN.methodParameterIsNull("listener");
        }
        if (readTxn instanceof UpdateTransaction) {
            safeCallListener((Listener<Object>)listener, readTxn);
            return true;
        }
        synchronized (lock) {
            assert runningTxns > 0;
            if (!pendingTxns.isEmpty()) {
                // cannot be upgraded because there are some pending updating txns registered already
                return false;
            }
            if (runningTxns == 1) {
                updatingTxnRunning = true;
            } else {
                final BasicUpdateTransaction upgradedTxn = new BasicUpdateTransaction(basicReadTxn);
                basicReadTxn.setWrappingTransaction(upgradedTxn);
                pendingTxns.add(new PendingTxnEntry(upgradedTxn, (Listener<Object>)listener));
                runningTxns--;
                return true;
            }
        }
        final BasicUpdateTransaction upgradedTxn = new BasicUpdateTransaction(basicReadTxn);
        basicReadTxn.setWrappingTransaction(upgradedTxn);
        safeCallListener((Listener<Object>)listener, upgradedTxn);
        return true;
    }

    private static final class PendingTxnEntry {
        private final Transaction txn;
        private final Listener<Object> listener;

        private PendingTxnEntry(final Transaction txn, final Listener<Object> listener) {
            this.txn = txn;
            this.listener = listener;
        }
    }

    @SuppressWarnings("unchecked")
    private void registerUpdateTransaction(final UpdateTransaction updateTxn, final Listener<? super UpdateTransaction> listener) {
        synchronized (lock) {
            if (runningTxns == 0) {
                updatingTxnRunning = true;
                runningTxns++;
            } else {
                pendingTxns.add(new PendingTxnEntry(updateTxn, (Listener<Object>)listener));
                return;
            }
        }
        safeCallListener((Listener<Object>)listener, updateTxn);
    }

    @SuppressWarnings("unchecked")
    private void registerReadTransaction(final ReadTransaction readTxn, final Listener<? super ReadTransaction> listener) {
        synchronized (lock) {
            if (runningTxns == 0) {
                runningTxns++;
            } else if (!updatingTxnRunning && pendingTxns.isEmpty()) {
                runningTxns++;
            } else {
                pendingTxns.add(new PendingTxnEntry(readTxn, (Listener<Object>)listener));
                return;
            }
        }
        safeCallListener((Listener<Object>)listener, readTxn);
    }

    void unregister() {
        Deque<PendingTxnEntry> notifications = null;
        synchronized (lock) {
            assert runningTxns > 0;
            runningTxns--;
            if (runningTxns == 0) {
                updatingTxnRunning = false;
                if (pendingTxns.isEmpty()) return;
                notifications = getNotifications();
            }
        }
        for (final PendingTxnEntry notification : notifications) {
            safeCallListener(notification.listener, notification.txn);
        }
    }

    private Deque<PendingTxnEntry> getNotifications() {
        assert Thread.holdsLock(lock);
        final Deque<PendingTxnEntry> notifications = new ArrayDeque<>();
        PendingTxnEntry entry = pendingTxns.removeFirst();
        notifications.add(entry);
        runningTxns++;
        if (entry.txn instanceof UpdateTransaction) {
            // process single updating transaction at the head
            updatingTxnRunning = true;
        } else {
            // process remaining read-only transactions at the head
            final Iterator<PendingTxnEntry> i = pendingTxns.iterator();
            while (i.hasNext()) {
                entry = i.next();
                if (entry.txn instanceof UpdateTransaction) break;
                i.remove();
                notifications.add(entry);
                runningTxns++;
            }
        }
        return notifications;
    }

    @SuppressWarnings("unchecked")
    private void safeCallListener(final Listener<Object> completionListener, final Transaction txn) {
        try {
            completionListener.handleEvent(txn);
        } catch (final Throwable t) {
            MSCLogger.ROOT.transactionCreationCompletionListenerFailed(t);
        }
    }

    /**
     * Creates a new service container.
     *
     * @return new service container.
     */
    public ServiceContainer newServiceContainer() {
        return new ServiceContainerImpl(this);
    }

    /**
     * Creates a new service context.
     *
     * @param transaction update transaction to associate service context with
     * @return new service context
     * @throws IllegalArgumentException if any parameter is null
     * @throws SecurityException if there's a <B>TransactionController</B> mismatch
     */
    public ServiceContext newServiceContext(final UpdateTransaction transaction) {
        validateTransaction(transaction);
        return new ServiceContextImpl(transaction);
    }
    
    /**
     * Asks updating {@code transaction} to transition to <B>PREPARED</B> state. This method is asynchronous i.e. once it is
     * finished the associated {@code completionListener} will be called.
     * Once this method returns, either {@link #commit(Transaction, Listener)} or {@link #restart(UpdateTransaction, Listener)} must be called
     * in order to release its allocated resources.
     *
     * @param transaction        the transaction to be prepared
     * @param completionListener the listener to call when the prepare is complete or has failed
     * @throws InvalidTransactionStateException if the transaction has already been prepared, restarted or committed
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public void prepare(final UpdateTransaction transaction, final Listener<? super UpdateTransaction> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        getAbstractTransaction(transaction).prepare(completionListener);
    }

    /**
     * Restarts <B>PREPARED</B> updating transaction to emulate compensating transaction behavior. This method is asynchronous i.e. once it is
     * finished the associated {@code completionListener} will be called. The updating transaction associated with
     * completion listener will always be different from updating {@code transaction} that was restarted.
     * In other words {@code transaction} passed as first parameter will be marked as <B>TERMINATED</B> once this method finishes its execution
     * and new updating transaction in <B>ACTIVE</B> state will be created.
     *
     * @param transaction        the transaction to be restarted
     * @param completionListener the listener to call when the restart is complete
     * @throws IllegalArgumentException if any parameter is null
     * @throws SecurityException if there's a <B>TransactionController</B> mismatch
     * @throws InvalidTransactionStateException if transaction is not in prepared state
     */
    public void restart(final UpdateTransaction transaction, final Listener<? super UpdateTransaction> completionListener) throws IllegalArgumentException, SecurityException, InvalidTransactionStateException {
        final BasicUpdateTransaction transactionImpl = validateTransaction(transaction);
        final Listener<UpdateTransaction> restartObserver = new Listener<UpdateTransaction>() {
            @Override
            public void handleEvent(final UpdateTransaction result) {
                final BasicUpdateTransaction retVal;
                synchronized (lock) {
                    retVal = new BasicUpdateTransaction(new BasicReadTransaction(TransactionController.this, transactionImpl.getExecutor()));
                }
                completionListener.handleEvent(retVal);
            }
        };
        getAbstractTransaction(transaction).restart(restartObserver);
    }

    /**
     * Commits the work done by {@link #prepare(UpdateTransaction, Listener)} and terminates the {@code transaction}.
     *
     * @param transaction        the transaction to be committed
     * @param completionListener the listener to call when the commit is complete
     * @throws InvalidTransactionStateException if the transaction has already been committed or has not yet been prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    @SuppressWarnings("unchecked")
    public <T extends Transaction> void commit(final T transaction, final Listener<T> completionListener) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        getAbstractTransaction(transaction).commit(completionListener);
    }

    /**
     * Indicates whether a <B>PREPARED</B> transaction can be committed.  If it cannot, it should be reverted with
     * compensating transaction created via {@link #restart(UpdateTransaction, Listener)} method.
     *
     * @param transaction the transaction
     * @return {@code true} if the transaction can be committed, {@code false} if it must be aborted
     * @throws InvalidTransactionStateException if the transaction is not prepared
     * @throws SecurityException if transaction was not created by this controller
     */
    public boolean canCommit(final Transaction transaction) throws InvalidTransactionStateException, SecurityException {
        validateTransaction(transaction);
        return getAbstractTransaction(transaction).canCommit();
    }

    /**
     * Determines whether the specified transaction have been created by this controller.
     * @param transaction to be checked
     * @return <code>true</code> if {@code transaction} have been created by this controller, <code>false</code> otherwise
     */
    public boolean owns(final Transaction transaction) {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        final boolean isUpdateTransaction = transaction instanceof BasicUpdateTransaction && ((BasicUpdateTransaction)transaction).getDelegate().txnController == this;
        final boolean isReadTransaction = transaction instanceof BasicReadTransaction && ((BasicReadTransaction)transaction).txnController == this;
        return isUpdateTransaction || isReadTransaction;
    }

    private void validateTransaction(final Transaction transaction) throws SecurityException {
        if (transaction == null) {
            throw TXN.methodParameterIsNull("transaction");
        }
        if (!owns(transaction)) {
            throw new SecurityException("Transaction not created by this controller");
        }
    }

    private BasicReadTransaction validateTransaction(final ReadTransaction readTxn) throws SecurityException {
        if (readTxn == null) {
            throw TXN.methodParameterIsNull("readTxn");
        }
        final boolean isReadTxn = readTxn instanceof BasicReadTransaction;
        final boolean isUpdateTxn = readTxn instanceof BasicUpdateTransaction;
        if (!isReadTxn && !isUpdateTxn) {
            throw new SecurityException("Transaction not created by this controller");
        }
        final BasicReadTransaction basicReadTxn = isUpdateTxn ? ((BasicUpdateTransaction)readTxn).getDelegate() : (BasicReadTransaction) readTxn;
        if (basicReadTxn.txnController != this) {
            throw new SecurityException("Transaction not created by this controller");
        }
        return basicReadTxn;
    }

    private BasicUpdateTransaction validateTransaction(final UpdateTransaction updateTxn) throws IllegalArgumentException, SecurityException {
        if (updateTxn == null) {
            throw TXN.methodParameterIsNull("updateTxn");
        }
        if (!(updateTxn instanceof BasicUpdateTransaction)) {
            throw new SecurityException("Transaction not created by this controller");
        }
        final BasicUpdateTransaction basicUpdateTxn = (BasicUpdateTransaction)updateTxn;
        if (basicUpdateTxn.getController() != this) {
            throw new SecurityException("Transaction not created by this controller");
        }
        return basicUpdateTxn;
    }
}
