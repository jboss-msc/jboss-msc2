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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

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

    private static final int FLAG_PREPARE_REQ         = 1 << 2;
    private static final int FLAG_COMMIT_REQ          = 1 << 3;
    private static final int FLAG_DO_PREPARE_LISTENER = 1 << 4;
    private static final int FLAG_DO_COMMIT_LISTENER  = 1 << 5;
    private static final int FLAG_SEND_COMMIT_REQ     = 1 << 6;
    private static final int FLAG_RESTARTED           = 1 << 7;
    private static final int FLAG_CLEAN_UP            = 1 << 8;
    private static final int FLAG_USER_THREAD         = 1 << 31;

    private static final int STATE_ACTIVE     = 0x0;
    private static final int STATE_PREPARED   = 0x1;
    private static final int STATE_COMMITTING = 0x2;
    private static final int STATE_COMMITTED  = 0x3;
    private static final int STATE_MASK       = 0x03;
    private static final int LISTENERS_MASK = FLAG_DO_PREPARE_LISTENER | FLAG_DO_COMMIT_LISTENER;
    private static final int PERSISTENT_STATE = STATE_MASK | FLAG_PREPARE_REQ | FLAG_COMMIT_REQ | FLAG_RESTARTED;

    private static final int T_NONE                    = 0;
    private static final int T_ACTIVE_to_PREPARED      = 1;
    private static final int T_PREPARED_to_COMMITTING  = 2;
    private static final int T_COMMITTING_to_COMMITTED = 3;
    final TransactionController txnController;
    final Executor taskExecutor;
    final Problem.Severity maxSeverity;
    private final long startTime = System.nanoTime();
    private final Queue<TaskControllerImpl<?>> topLevelTasks = new ConcurrentLinkedQueue<>();
    private final ProblemReport report = new ProblemReport();
    final TaskParent topParent = new TaskParent() {

        public void childExecuted(final boolean userThread) {
            doChildExecuted(userThread);
        }

        public void childTerminated(final boolean userThread) {
            doChildTerminated(userThread);
        }

        public void childAdded(final TaskChild child, final boolean userThread) throws InvalidTransactionStateException {
            doChildAdded((TaskControllerImpl<?>) child, userThread);
        }

        public Transaction getTransaction() {
            return wrappingTxn;
        }

    };

    private final TaskFactory taskFactory = new TaskFactory() {
        public final <T> TaskBuilder<T> newTask(Executable<T> task) throws IllegalStateException {
            return new TaskBuilderImpl<>(wrappingTxn, topParent, task);
        }
    };
    private long endTime;
    private int state;
    private int uncancelledChildren;
    private int unexecutedChildren;
    private int unterminatedChildren;
    private Listener<? super PrepareResult<? extends Transaction>> prepareListener;
    private Listener<? super CommitResult<? extends Transaction>> commitListener;
    private List<PrepareCompletionListener> prepareCompletionListeners = new ArrayList<>(0);
    private List<TerminateCompletionListener> terminateCompletionListeners = new ArrayList<>(0);
    private volatile Transaction wrappingTxn;

    AbstractTransaction(final TransactionController txnController, final Executor taskExecutor, final Problem.Severity maxSeverity) {
        this.txnController = txnController;
        this.taskExecutor = taskExecutor;
        this.maxSeverity = maxSeverity;
    }

    void setWrappingTransaction(final Transaction wrappingTxn) {
        this.wrappingTxn = wrappingTxn;
    }

    final void addListener(final PrepareCompletionListener listener) {
        synchronized (this) {
            if (prepareCompletionListeners != null) {
                prepareCompletionListeners.add(listener);
                return;
            }
        }
        throw new InvalidTransactionStateException();
    }

    final void addListener(final TerminateCompletionListener listener) {
        synchronized (this) {
            if (terminateCompletionListeners != null) {
                terminateCompletionListeners.add(listener);
                return;
            }
        }
        throw new InvalidTransactionStateException();
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
                if (Bits.allAreSet(state, FLAG_PREPARE_REQ) && unexecutedChildren == 0 && uncancelledChildren == 0) {
                    return T_ACTIVE_to_PREPARED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_PREPARED: {
                if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                    return T_PREPARED_to_COMMITTING;
                } else {
                    return T_NONE;
                }
            }
            case STATE_COMMITTING: {
                if (unterminatedChildren == 0) {
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
                case T_ACTIVE_to_PREPARED: {
                    state = newState(STATE_PREPARED, state | FLAG_DO_PREPARE_LISTENER);
                    continue;
                }
                case T_PREPARED_to_COMMITTING: {
                    state = newState(STATE_COMMITTING, state | FLAG_SEND_COMMIT_REQ);
                    continue;
                }
                case T_COMMITTING_to_COMMITTED: {
                    state = newState(STATE_COMMITTED, state | FLAG_DO_COMMIT_LISTENER | FLAG_CLEAN_UP);
                    continue;
                }
                default: throw new IllegalStateException();
            }
        }
    }

    private final Runnable commitTask = new Runnable() {
        public void run() {
            callTerminateCompletionListeners();
            callCommitListener();
        }
    };

    private final Runnable prepareTask = new Runnable() {
        public void run() {
            callPrepareCompletionListeners();
            callPrepareListener();
        }
    };

    private final Runnable cleanUpTask = new Runnable() {
        public void run() {
            txnController.unregister();
        }
    };

    private class SendCommitRequestTask implements Runnable {
        private final boolean userThread;

        SendCommitRequestTask(final boolean userThread) {
            this.userThread = userThread;
        }

        public void run() {
            for (TaskControllerImpl<?> task : topLevelTasks) {
                task.childCommit(userThread);
            }
        }
    }

    private void executeTasks(final int state) {
        final boolean userThread = Bits.allAreSet(state, FLAG_USER_THREAD);
        if (userThread) {
            if (Bits.anyAreSet(state, LISTENERS_MASK)) {
                safeExecute(new AsyncTask(state & (PERSISTENT_STATE | LISTENERS_MASK)));
            }
        } else {
            if (Bits.allAreSet(state, FLAG_DO_COMMIT_LISTENER)) {
                ThreadLocalExecutor.addTask(commitTask);
            }
            if (Bits.allAreSet(state, FLAG_DO_PREPARE_LISTENER)) {
                ThreadLocalExecutor.addTask(prepareTask);
            }
        }
        if (Bits.allAreSet(state, FLAG_CLEAN_UP)) {
            ThreadLocalExecutor.addTask(cleanUpTask);
        }
        if (Bits.allAreSet(state, FLAG_SEND_COMMIT_REQ)) {
            ThreadLocalExecutor.addTask(new SendCommitRequestTask(userThread));
        }
        ThreadLocalExecutor.executeTasks();
    }

    private void safeExecute(final Runnable command) {
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

    static void safeCallListener(final TerminateCompletionListener listener) {
        try {
            listener.transactionTerminated();
        } catch (final Throwable t) {
            MSCLogger.ROOT.terminateCompletionListenerFailed(t);
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
            if (Bits.allAreSet(state, FLAG_RESTARTED)) {
                throw MSCLogger.TXN.cannotRestartRestartedTxn();
            }
            if (stateOf(state) != STATE_PREPARED) {
                throw MSCLogger.TXN.cannotRestartUnpreparedTxn();
            }
            unterminatedChildren = 0;
            this.state = FLAG_RESTARTED;
            topLevelTasks.clear();
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
        try {
            commit(null);
        } finally {
            try {
                super.finalize();
            } catch (Throwable ignored) {
            }
        }
    }

    private void doChildExecuted(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unexecutedChildren--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void doChildTerminated(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unterminatedChildren--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void doChildAdded(final TaskControllerImpl<?> child, final boolean userThread) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateOf(state) != STATE_ACTIVE) {
                throw MSCLogger.TXN.cannotAddChildToInactiveTxn(stateOf(state));
            }
            if (userThread) state |= FLAG_USER_THREAD;
            topLevelTasks.add(child);
            unexecutedChildren++;
            unterminatedChildren++;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void childCancelRequested(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateOf(state) != STATE_ACTIVE) {
                throw MSCLogger.TXN.cannotCancelChildOnInactiveTxn(stateOf(state));
            }
            uncancelledChildren++;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void childCancelled(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateOf(state) != STATE_ACTIVE) {
                throw MSCLogger.TXN.cannotCancelChildOnInactiveTxn(stateOf(state));
            }
            uncancelledChildren--;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void adoptGrandchildren(final Queue<TaskControllerImpl<?>> grandchildren, final boolean userThread, final int unexecutedGreatGrandchildren, final int unterminatedGreatGrandchildren) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            topLevelTasks.addAll(grandchildren);
            unexecutedChildren += unexecutedGreatGrandchildren;
            unterminatedChildren += unterminatedGreatGrandchildren;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
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
        final List<PrepareCompletionListener> prepareCompletionListeners;
        synchronized (this) {
            prepareCompletionListeners = this.prepareCompletionListeners;
            this.prepareCompletionListeners = new ArrayList<>(0);
        }
        for (final PrepareCompletionListener listener : prepareCompletionListeners) {
            safeCallListener(listener);
        }
        prepareCompletionListeners.clear();
    }

    private void callTerminateCompletionListeners() {
        final List<TerminateCompletionListener> terminateCompletionListeners;
        synchronized (this) {
            terminateCompletionListeners = this.terminateCompletionListeners;
            this.terminateCompletionListeners = new ArrayList<>(0);
        }
        for (final TerminateCompletionListener listener : terminateCompletionListeners) {
            safeCallListener(listener);
        }
        terminateCompletionListeners.clear();
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
