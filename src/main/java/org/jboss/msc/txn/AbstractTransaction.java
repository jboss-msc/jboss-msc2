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
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    private static final int FLAG_PREPARE_REQ     = 1 << 3;
    private static final int FLAG_RESTART_REQ     = 1 << 4;
    private static final int FLAG_COMMIT_REQ      = 1 << 5;
    private static final int FLAG_DO_POST_PREPARE = 1 << 6;
    private static final int FLAG_DO_PREPARE      = 1 << 7;
    private static final int FLAG_DO_POST_RESTART = 1 << 8;
    private static final int FLAG_DO_RESTART      = 1 << 9;
    private static final int FLAG_DO_POST_COMMIT  = 1 << 10;
    private static final int FLAG_DO_COMMIT       = 1 << 11;
    private static final int FLAG_DO_CLEAN_UP     = 1 << 12;
    private static final int FLAG_USER_THREAD     = 1 << 31;

    private static final int STATE_ACTIVE     = 0x0;
    private static final int STATE_PREPARING  = 0x1;
    private static final int STATE_PREPARED   = 0x2;
    private static final int STATE_RESTARTING = 0x3;
    private static final int STATE_COMMITTING = 0x4;
    private static final int STATE_COMMITTED  = 0x5;
    private static final int STATE_MASK       = 0x7;
    private static final int LISTENERS_MASK = FLAG_DO_POST_PREPARE | FLAG_DO_PREPARE | FLAG_DO_POST_RESTART | FLAG_DO_RESTART | FLAG_DO_POST_COMMIT | FLAG_DO_COMMIT;
    private static final int PERSISTENT_STATE = STATE_MASK | FLAG_PREPARE_REQ | FLAG_COMMIT_REQ | FLAG_RESTART_REQ;

    private static final int T_NONE                    = 0;
    private static final int T_ACTIVE_to_PREPARING     = 1;
    private static final int T_PREPARING_to_PREPARED   = 2;
    private static final int T_PREPARED_to_RESTARTING  = 3;
    private static final int T_RESTARTING_to_COMMITTED = 4;
    private static final int T_PREPARED_to_COMMITTING  = 5;
    private static final int T_COMMITTING_to_COMMITTED = 6;
    final TransactionController txnController;
    final Executor taskExecutor;
    private final Problem.Severity maxSeverity = Problem.Severity.WARNING;
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
    private Listener<? super UpdateTransaction> prepareListener;
    private Listener<? super UpdateTransaction> restartListener;
    private Listener<Transaction> commitListener;
    private final Object listenersLock = new Object();
    private Deque<PrepareCompletionListener> prepareCompletionListeners = new ArrayDeque<>();
    volatile Transaction wrappingTxn;

    AbstractTransaction(final TransactionController txnController, final Executor taskExecutor) {
        this.txnController = txnController;
        this.taskExecutor = taskExecutor;
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

    public final boolean isPrepared() {
        assert ! holdsLock(this);
        synchronized (this) {
            return stateOf(state) == STATE_PREPARED;
        }
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

    public final Executor getExecutor() {
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
                if (Bits.allAreSet(state, FLAG_PREPARE_REQ) && unexecutedTasks.get() == 0 && holdHandles.isEmpty()) {
                    return T_ACTIVE_to_PREPARING;
                } else {
                    return T_NONE;
                }
            }
            case STATE_PREPARING: {
                if (uncompletedPostPrepareListeners.get() == 0) {
                    return T_PREPARING_to_PREPARED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_PREPARED: {
                if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                    return T_PREPARED_to_COMMITTING;
                } else if (Bits.allAreSet(state, FLAG_RESTART_REQ)) {
                    return T_PREPARED_to_RESTARTING;
                } else {
                    return T_NONE;
                }
            }
            case STATE_RESTARTING: {
                if (uncompletedPostRestartListeners.get() == 0) {
                    return T_RESTARTING_to_COMMITTED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_COMMITTING: {
                if (uncompletedPostCommitListeners.get() == 0) {
                    return T_COMMITTING_to_COMMITTED;
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
                case T_ACTIVE_to_PREPARING: {
                    if (postPrepareListeners.size() > 0) {
                        uncompletedPostPrepareListeners.set(postPrepareListeners.size());
                        state = newState(STATE_PREPARING, state | FLAG_DO_POST_PREPARE);
                    } else {
                        state = newState(STATE_PREPARING, state);
                    }
                    continue;
                }
                case T_PREPARING_to_PREPARED: {
                    state = newState(STATE_PREPARED, state | FLAG_DO_PREPARE);
                    continue;
                }
                case T_PREPARED_to_RESTARTING: {
                    if (postRestartListeners.size() > 0) {
                        uncompletedPostRestartListeners.set(postRestartListeners.size());
                        state = newState(STATE_RESTARTING, state | FLAG_DO_POST_RESTART);
                    } else {
                        state = newState(STATE_RESTARTING, state);
                    }
                    continue;
                }
                case T_RESTARTING_to_COMMITTED: {
                    state = newState(STATE_COMMITTED, state | FLAG_DO_RESTART);
                    continue;
                }
                case T_PREPARED_to_COMMITTING: {
                    if (postCommitListeners.size() > 0) {
                        uncompletedPostCommitListeners.set(postCommitListeners.size());
                        state = newState(STATE_COMMITTING, state | FLAG_DO_POST_COMMIT);
                    } else {
                        state = newState(STATE_COMMITTING, state);
                    }
                    continue;
                }
                case T_COMMITTING_to_COMMITTED: {
                    state = newState(STATE_COMMITTED, state | FLAG_DO_COMMIT | FLAG_DO_CLEAN_UP);
                    continue;
                }
                default: throw new IllegalStateException();
            }
        }
    }

    private final Runnable postPrepareTask = new Runnable() {
        public void run() {
            callPostPrepareListeners();
        }
    };

    private final Runnable prepareTask = new Runnable() {
        public void run() {
            callPrepareCompletionListeners();
            callPrepareListener();
        }
    };

    private final Runnable postRestartTask = new Runnable() {
        public void run() {
            callPostRestartListeners();
        }
    };

    private final Runnable restartTask = new Runnable() {
        public void run() {
            callRestartListener();
        }
    };

    private final Runnable postCommitTask = new Runnable() {
        public void run() {
            callPostCommitListeners();
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
            if (Bits.allAreSet(state, FLAG_DO_POST_COMMIT)) {
                ThreadLocalExecutor.addTask(postCommitTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_RESTART)) {
                ThreadLocalExecutor.addTask(restartTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_POST_RESTART)) {
                ThreadLocalExecutor.addTask(postRestartTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_PREPARE)) {
                ThreadLocalExecutor.addTask(prepareTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_POST_PREPARE)) {
                ThreadLocalExecutor.addTask(postPrepareTask);
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

    void safeCallPostPrepareListener(final Action listener) {
        try {
            listener.handleEvent(new PrepareActionContext(this));
        } catch (final Throwable t) {
            MSCLogger.ROOT.postPrepareCompletionListenerFailed(t);
        }
    }

    void safeCallPostRestartListener(final Action listener) {
        try {
            listener.handleEvent(new RestartActionContext(this));
        } catch (final Throwable t) {
            MSCLogger.ROOT.postRestartCompletionListenerFailed(t);
        }
    }

    void safeCallPostCommitListener(final Action listener) {
        try {
            listener.handleEvent(new CommitActionContext(this));
        } catch (final Throwable t) {
            MSCLogger.ROOT.postCommitCompletionListenerFailed(t);
        }
    }

    final void prepare(final Listener<? super UpdateTransaction> completionListener) throws InvalidTransactionStateException {
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

    final void commit(final Listener<? extends Transaction> completionListener) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            if (wrappingTxn instanceof UpdateTransaction && stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotCommitUnpreparedTxn();
            } else {
                state |= FLAG_PREPARE_REQ;
            }
            if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                throw MSCLogger.TXN.cannotCommitCommittedTxn();
            }
            state |= FLAG_COMMIT_REQ;
            commitListener = (Listener<Transaction>)completionListener;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    final void restart(final Listener<? super UpdateTransaction> completionListener) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            if (Bits.allAreSet(state, FLAG_RESTART_REQ)) {
                throw MSCLogger.TXN.cannotRestartRestartedTxn();
            }
            if (stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotRestartUnpreparedTxn();
            }
            state |= FLAG_RESTART_REQ;
            restartListener = completionListener;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
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
        final Listener<? super UpdateTransaction> prepareListener;
        synchronized (this) {
            prepareListener = this.prepareListener;
            this.prepareListener = null;
        }
        callListeners(prepareListener, null, null);
    }

    private void callRestartListener() {
        final Listener<? super UpdateTransaction> restartListener;
        synchronized (this) {
            restartListener = this.restartListener;
            this.restartListener = null;
        }
        callListeners(null, restartListener, null);
    }

    private void callCommitListener() {
        final Listener<Transaction> commitListener;
        synchronized (this) {
            endTime = System.nanoTime();
            commitListener = this.commitListener;
            this.commitListener = null;
        }
        callListeners(null, null, commitListener);
    }

    private void callListeners(
        final Listener<? super UpdateTransaction> prepareListener,
        final Listener<? super UpdateTransaction> restartListener,
        final Listener<Transaction> commitListener) {
        if (prepareListener != null) {
            try {
                prepareListener.handleEvent((UpdateTransaction)wrappingTxn);
            } catch (final Throwable ignored) {
                MSCLogger.ROOT.listenerFailed(ignored, prepareListener);
            }
        }
        if (restartListener != null) {
            try {
                restartListener.handleEvent((UpdateTransaction)wrappingTxn);
            } catch (final Throwable ignored) {
                MSCLogger.ROOT.listenerFailed(ignored, restartListener);
            }
        }
        if (commitListener != null) {
            try {
                commitListener.handleEvent(wrappingTxn);
            } catch (final Throwable ignored) {
                MSCLogger.ROOT.listenerFailed(ignored, commitListener);
            }
        }
    }

    private final Set<TransactionHoldHandle> holdHandles = new IdentityHashSet<>();

    public final synchronized TransactionHoldHandle acquireHoldHandle() {
        if (stateOf(state) != STATE_ACTIVE) {
            throw MSCLogger.TXN.cannotCreateHoldHandle();
        }
        final TransactionHoldHandle retVal = new TransactionHoldHandle(this);
        holdHandles.add(retVal);
        return retVal;
    }

    public final void release(final TransactionHoldHandle txnHoldHandle) {
        int state;
        synchronized (this) {
            holdHandles.remove(txnHoldHandle);
            state = transition(this.state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    public final Set<Action> postPrepareListeners = new IdentityHashSet<>();
    public final AtomicInteger uncompletedPostPrepareListeners = new AtomicInteger();
    public final Set<Action> postRestartListeners = new IdentityHashSet<>();
    public final AtomicInteger uncompletedPostRestartListeners = new AtomicInteger();
    public final Set<Action> postCommitListeners = new IdentityHashSet<>();
    public final AtomicInteger uncompletedPostCommitListeners = new AtomicInteger();

    public final synchronized void addPostPrepare(final Action completionListener) {
        if (stateOf(state) != STATE_ACTIVE) throw MSCLogger.TXN.cannotAddPostPrepareListener();
        if (completionListener != null) postPrepareListeners.add(completionListener);
    }

    public final synchronized void removePostPrepare(final Action completionListener) {
        if (stateOf(state) != STATE_ACTIVE) throw MSCLogger.TXN.cannotRemovePostPrepareListener();
        if (completionListener != null) postPrepareListeners.remove(completionListener);
    }

    public final synchronized void addPostRestart(final Action completionListener) {
        if (stateOf(state) > STATE_PREPARED) throw MSCLogger.TXN.cannotAddPostRestartListener();
        if (completionListener != null) postRestartListeners.add(completionListener);
    }

    public final synchronized void removePostRestart(final Action completionListener) {
        if (stateOf(state) > STATE_PREPARED) throw MSCLogger.TXN.cannotRemovePostRestartListener();
        if (completionListener != null) postRestartListeners.remove(completionListener);
    }

    public final synchronized void addPostCommit(final Action completionListener) {
        if (stateOf(state) == STATE_COMMITTED) throw MSCLogger.TXN.cannotAddPostCommitListener();
        if (completionListener != null) postCommitListeners.add(completionListener);
    }

    public final synchronized void removePostCommit(final Action completionListener) {
        if (stateOf(state) == STATE_COMMITTED) throw MSCLogger.TXN.cannotRemovePostCommitListener();
        if (completionListener != null) postCommitListeners.remove(completionListener);
    }

    private void callPostPrepareListeners() {
        for (final Action action : postPrepareListeners ) {
            safeCallPostPrepareListener(action);
        }
    }

    private void postPrepareListenerCompleted() {
        if (uncompletedPostPrepareListeners.decrementAndGet() > 0) return;
        int state;
        synchronized (this) {
            state = transition(this.state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void callPostRestartListeners() {
        for (final Action action : postRestartListeners ) {
            safeCallPostRestartListener(action);
        }
    }

    private void postRestartListenerCompleted() {
        if (uncompletedPostRestartListeners.decrementAndGet() > 0) return;
        int state;
        synchronized (this) {
            state = transition(this.state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void callPostCommitListeners() {
        for (final Action action : postCommitListeners ) {
            safeCallPostCommitListener(action);
        }
    }

    private void postCommitListenerCompleted() {
        if (uncompletedPostCommitListeners.decrementAndGet() > 0) return;
        int state;
        synchronized (this) {
            state = transition(this.state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private static final class PrepareActionContext implements ActionContext {

        private final AbstractTransaction txn;
        private final AtomicBoolean completed = new AtomicBoolean();

        private PrepareActionContext(final AbstractTransaction txn) {
            this.txn = txn;
        }

        @Override
        public Transaction getTransaction() {
            return txn.wrappingTxn;
        }

        @Override
        public void complete() {
            if (!completed.compareAndSet(false, true)) return;
            txn.postPrepareListenerCompleted();
        }
    }

    private static final class RestartActionContext implements ActionContext {

        private final AbstractTransaction txn;
        private final AtomicBoolean completed = new AtomicBoolean();

        private RestartActionContext(final AbstractTransaction txn) {
            this.txn = txn;
        }

        @Override
        public Transaction getTransaction() {
            return txn.wrappingTxn;
        }

        @Override
        public void complete() {
            if (!completed.compareAndSet(false, true)) return;
            txn.postRestartListenerCompleted();
        }
    }

    private static final class CommitActionContext implements ActionContext {

        private final AbstractTransaction txn;
        private final AtomicBoolean completed = new AtomicBoolean();

        private CommitActionContext(final AbstractTransaction txn) {
            this.txn = txn;
        }

        @Override
        public Transaction getTransaction() {
            return txn.wrappingTxn;
        }

        @Override
        public void complete() {
            if (!completed.compareAndSet(false, true)) return;
            txn.postCommitListenerCompleted();
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
