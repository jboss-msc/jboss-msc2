/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc._private.Version;
import org.jboss.msc.problem.Problem;
import org.jboss.msc.problem.ProblemReport;
import org.jboss.msc.util.Listener;
import org.jboss.msc.util.SimpleAttachable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Thread.holdsLock;

/**
 * A transaction.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
abstract class AbstractTransaction extends SimpleAttachable implements Transaction {

    static {
        MSCLogger.ROOT.greeting(Version.getVersionString());
    }

    private static final int FLAG_PREPARE_REQ = 1 << 2;
    private static final int FLAG_RESTART_REQ = 1 << 3;
    private static final int FLAG_COMMIT_REQ  = 1 << 4;
    private static final int FLAG_DO_PREPARE  = 1 << 5;
    private static final int FLAG_DO_COMMIT   = 1 << 6;
    private static final int FLAG_DO_CLEAN_UP = 1 << 7;
    private static final int FLAG_USER_THREAD = 1 << 31;

    private static final int STATE_ACTIVE    = 0x0;
    private static final int STATE_PREPARED  = 0x1;
    private static final int STATE_COMMITTED = 0x2;
    private static final int STATE_MASK      = 0x3;
    private static final int LISTENERS_MASK = FLAG_DO_PREPARE | FLAG_DO_COMMIT;
    private static final int PERSISTENT_STATE = STATE_MASK | FLAG_PREPARE_REQ | FLAG_COMMIT_REQ | FLAG_RESTART_REQ;

    private static final int T_NONE                  = 0;
    private static final int T_ACTIVE_to_PREPARED    = 1;
    private static final int T_PREPARED_to_COMMITTED = 2;
    final TransactionController txnController;
    final Executor taskExecutor;
    final Problem.Severity maxSeverity;
    private final long startTime = System.nanoTime();
    private final ProblemReport report = new ProblemReport();
    private final TaskFactory taskFactory = new TaskFactory() {
        public final <T> TaskBuilder<T> newTask(Executable<T> task) throws IllegalStateException {
            return new TaskBuilderImpl<>(AbstractTransaction.this, task);
        }
    };
    private long endTime;
    private int state;
    private final AtomicInteger unexecutedTasks = new AtomicInteger();
    private Listener<? super PrepareResult<? extends Transaction>> prepareListener;
    private Listener<? super CommitResult<? extends Transaction>> commitListener;
    private final Object listenersLock = new Object();
    private Deque<PrepareCompletionListener> prepareCompletionListeners = new ArrayDeque<>();
    volatile Transaction wrappingTxn;

    AbstractTransaction(final TransactionController txnController, final Executor taskExecutor, final Problem.Severity maxSeverity) {
        this.txnController = txnController;
        this.taskExecutor = taskExecutor;
        this.maxSeverity = maxSeverity;
    }

    void setWrappingTransaction(final Transaction wrappingTxn) {
        this.wrappingTxn = wrappingTxn;
    }

    final void addListener(final PrepareCompletionListener listener) {
        synchronized (listenersLock) {
            prepareCompletionListeners.add(listener);
        }
    }

    private static int stateOf(final int val) {
        return val & STATE_MASK;
    }

    private static int newState(int sid, int oldState) {
        return sid & STATE_MASK | oldState & ~STATE_MASK;
    }

    public final boolean isTerminated() {
        assert ! holdsLock(this);
        synchronized (this) {
            return stateOf(state) == STATE_COMMITTED;
        }
    }

    public final long getDuration(TimeUnit unit) {
        assert ! holdsLock(this);
        synchronized (this) {
            if (stateOf(state) == STATE_COMMITTED) {
                return unit.convert(endTime - startTime, TimeUnit.NANOSECONDS);
            } else {
                return unit.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            }
        }
    }

    final Executor getExecutor() {
        return taskExecutor;
    }

    public final ProblemReport getReport() {
        return report;
    }

    /**
     * Calculate the transition to take from the current state.
     *
     * @param state the current state
     * @return the transition to take
     */
    private int getTransition(int state) {
        assert holdsLock(this);
        int sid = stateOf(state);
        switch (sid) {
            case STATE_ACTIVE: {
                if (Bits.allAreSet(state, FLAG_PREPARE_REQ) && unexecutedTasks.get() == 0) {
                    return T_ACTIVE_to_PREPARED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_PREPARED: {
                if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                    return T_PREPARED_to_COMMITTED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_COMMITTED: {
                return T_NONE;
            }
            default: throw new IllegalStateException();
        }
    }

    /**
     * Perform any necessary/possible transition.
     *
     * @param state the current state
     * @return the new state
     */
    private int transition(int state) {
        assert holdsLock(this);
        for (;;) {
            int t = getTransition(state);
            switch (t) {
                case T_NONE: return state;
                case T_ACTIVE_to_PREPARED: {
                    state = newState(STATE_PREPARED, state | FLAG_DO_PREPARE);
                    continue;
                }
                case T_PREPARED_to_COMMITTED: {
                    state = newState(STATE_COMMITTED, state | FLAG_DO_COMMIT | FLAG_DO_CLEAN_UP);
                    continue;
                }
                default: throw new IllegalStateException();
            }
        }
    }

    private final Runnable prepareTask = new Runnable() {
        public void run() {
            callPrepareCompletionListeners();
            callPrepareListener();
        }
    };

    private final Runnable commitTask = new Runnable() {
        public void run() {
            callCommitListener();
        }
    };

    private final Runnable cleanUpTask = new Runnable() {
        public void run() {
            txnController.unregister();
        }
    };

    private void executeTasks(final int state) {
        final boolean userThread = Bits.allAreSet(state, FLAG_USER_THREAD);
        if (userThread) {
            if (Bits.anyAreSet(state, LISTENERS_MASK)) {
                safeExecute(new AsyncTask(state & (PERSISTENT_STATE | LISTENERS_MASK)));
            }
        } else {
            if (Bits.allAreSet(state, FLAG_DO_COMMIT)) {
                ThreadLocalExecutor.addTask(commitTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_PREPARE)) {
                ThreadLocalExecutor.addTask(prepareTask);
            }
        }
        if (Bits.allAreSet(state, FLAG_DO_CLEAN_UP)) {
            ThreadLocalExecutor.addTask(cleanUpTask);
        }
        ThreadLocalExecutor.executeTasks();
    }

    void safeExecute(final Runnable command) {
        try {
            taskExecutor.execute(command);
        } catch (Throwable t) {
            MSCLogger.ROOT.runnableExecuteFailed(t, command);
        }
    }

    static void safeCallListener(final PrepareCompletionListener listener) {
        try {
            listener.transactionPrepared();
        } catch (final Throwable t) {
            MSCLogger.ROOT.prepareCompletionListenerFailed(t);
        }
    }

    final void prepare(final Listener<? super PrepareResult<? extends Transaction>> completionListener) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            if (stateOf(state) != STATE_ACTIVE) {
                throw MSCLogger.TXN.cannotPrepareNonActiveTxn();
            }
            if (Bits.allAreSet(state, FLAG_PREPARE_REQ)) {
                throw MSCLogger.TXN.cannotPreparePreparedTxn();
            }
            state |= FLAG_PREPARE_REQ;
            prepareListener = completionListener;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    final void commit(final Listener<? super CommitResult<? extends Transaction>> completionListener) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            if (stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotCommitUnpreparedTxn();
            }
            if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                throw MSCLogger.TXN.cannotCommitCommittedTxn();
            }
            state |= FLAG_COMMIT_REQ;
            commitListener = completionListener;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    final void restart() throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        synchronized (this) {
            if (Bits.allAreSet(state, FLAG_RESTART_REQ)) {
                throw MSCLogger.TXN.cannotRestartRestartedTxn();
            }
            if (stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotRestartUnpreparedTxn();
            }
            state = FLAG_RESTART_REQ; // resets all persistent state except RESTART flag
        }
    }

    final boolean canCommit() throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        synchronized (this) {
            if (stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotInspectUnpreparedTxn();
            }
        }
        return reportIsCommittable();
    }

    private boolean reportIsCommittable() {
        return report.getMaxSeverity().compareTo(maxSeverity) <= 0;
    }

    protected void finalize() {
        // TODO: this method is broken, fix it!
        try {
            commit(null);
        } finally {
            try {
                super.finalize();
            } catch (Throwable ignored) {
            }
        }
    }

    void taskExecuted() {
        assert ! holdsLock(this);
        if (unexecutedTasks.decrementAndGet() > 0) return;
        int state;
        synchronized (this) {
            state = this.state;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void taskAdded() throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        synchronized (this) {
            if (stateOf(state) != STATE_ACTIVE) {
                throw MSCLogger.TXN.cannotAddChildToInactiveTxn(stateOf(state));
            }
            unexecutedTasks.incrementAndGet();
        }
    }

    boolean isActive() {
        assert ! holdsLock(this);
        synchronized (this) {
            return stateOf(state) == STATE_ACTIVE;
        }
    }

    void ensureIsActive() {
        if (!isActive()) {
            throw MSCLogger.TXN.inactiveTransaction();
        }
    }

    private void callPrepareCompletionListeners() {
        final Deque<PrepareCompletionListener> prepareCompletionListeners;
        synchronized (listenersLock) {
            prepareCompletionListeners = this.prepareCompletionListeners;
            this.prepareCompletionListeners = new ArrayDeque<>();
        }
        for (final PrepareCompletionListener listener : prepareCompletionListeners) {
            safeCallListener(listener);
        }
        prepareCompletionListeners.clear();
    }

    private void callPrepareListener() {
        final Listener<? super PrepareResult<? extends Transaction>> prepareListener;
        synchronized (this) {
            prepareListener = this.prepareListener;
            this.prepareListener = null;
        }
        callListeners(prepareListener, null);
    }

    private void callCommitListener() {
        final Listener<? super CommitResult<? extends Transaction>> commitListener;
        synchronized (this) {
            endTime = System.nanoTime();
            commitListener = this.commitListener;
            this.commitListener = null;
        }
        callListeners(null, commitListener);
    }

    private void callListeners(
            final Listener<? super PrepareResult<? extends Transaction>> prepareListener,
            final Listener<? super CommitResult<? extends Transaction>> commitListener) {
        if (prepareListener != null) {
            try {
                prepareListener.handleEvent(new PrepareResult<Transaction>() {
                    @Override
                    public Transaction getTransaction() {
                        return wrappingTxn;
                    }
                });
            } catch (final Throwable ignored) {
                MSCLogger.ROOT.listenerFailed(ignored, prepareListener);
            }
        }
        if (commitListener != null) {
            try {
                commitListener.handleEvent(new CommitResult<Transaction>() {
                    @Override
                    public Transaction getTransaction() {
                        return wrappingTxn;
                    }
                });
            } catch (final Throwable ignored) {
                MSCLogger.ROOT.listenerFailed(ignored, commitListener);
            }
        }
    }

    final TaskFactory getTaskFactory() {
        return taskFactory;
    }

    class AsyncTask implements Runnable {
        private final int state;

        AsyncTask(final int state) {
            this.state = state;
        }

        public void run() {
            executeTasks(state);
        }

        @Override
        public String toString() {
            return wrappingTxn + ".AsyncTask@" + System.identityHashCode(this);
        }
    }
}
