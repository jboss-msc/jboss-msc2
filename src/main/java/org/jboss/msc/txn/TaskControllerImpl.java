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

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.Thread.holdsLock;

/**
 * A controller for an installed subtask.
 *
 * The following defines the state machine for this class.
 * <pre>
 *  +---------------+
 *  |               |
 *  |      NEW      |
 *  |               |
 *  +-------+-------+
 *          |
 *          |
 *          v
 *  +---------------+
 *  |               |
 *  |  EXECUTE_WAIT |
 *  |               |
 *  +-------+-------+
 *          |
 *          |
 *          v
 *  +---------------+
 *  |               |
 *  |    EXECUTE    |
 *  |               |
 *  +-------+-------+
 *          |
 *          |
 *          v
 *  +-----------------------+
 *  |                       |
 *  | EXECUTE_CHILDREN_WAIT |
 *  |                       |
 *  +-------+---------------+
 *          |
 *          |
 *          v
 *  +---------------+
 *  |               |
 *  |  EXECUTE_DONE |
 *  |               |
 *  +-------+-------+
 *          |
 *          | COMMIT_REQ
 *          v
 *  +----------------+
 *  |                |
 *  | TERMINATE_WAIT |
 *  |                |
 *  +----------------+
 *          |
 *          |
 *          v
 *  +----------------+
 *  |                |
 *  |   TERMINATED   |
 *  |                |
 *  +----------------+
 * </pre>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class TaskControllerImpl<T> implements TaskController<T>, TaskParent, TaskChild {

    private static final Object NO_RESULT = new Object();

    private static final ThreadLocal<ClassLoader> CL_HOLDER = new ThreadLocal<>();

    private final TaskParent parent;
    private final TaskControllerImpl<?>[] dependencies;
    private final Executable<T> executable;
    private final ClassLoader classLoader;
    private final ArrayList<TaskControllerImpl<?>> dependents = new ArrayList<>();
    private final Queue<TaskControllerImpl<?>> children = new ConcurrentLinkedQueue<>();

    private int state;
    private int unexecutedDependencies;
    private int unexecutedChildren;
    private int unterminatedChildren;

    @SuppressWarnings("unchecked")
    private volatile T result = (T) NO_RESULT;

    private static final ThreadLocal<TaskControllerImpl<?>[]> cachedDependents = new ThreadLocal<>();

    private static final int STATE_MASK        = 0xF;

    private static final int STATE_NEW                    = 0;
    private static final int STATE_EXECUTE_WAIT           = 1;
    private static final int STATE_EXECUTE                = 2;
    private static final int STATE_EXECUTE_CHILDREN_WAIT  = 3;
    private static final int STATE_EXECUTE_DONE           = 4;
    private static final int STATE_TERMINATE_WAIT         = 5;
    private static final int STATE_TERMINATED             = 6;
    private static final int STATE_LAST = STATE_TERMINATED;

    private static final int T_NONE = 0;

    private static final int T_NEW_to_EXECUTE_WAIT = 1;
    private static final int T_NEW_to_TERMINATED = 2;
    private static final int T_EXECUTE_WAIT_to_EXECUTE = 3;
    private static final int T_EXECUTE_to_EXECUTE_CHILDREN_WAIT = 4;
    private static final int T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE = 5;
    private static final int T_EXECUTE_DONE_to_TERMINATE_WAIT = 6;
    private static final int T_TERMINATE_WAIT_to_TERMINATED = 7;

    private static final int FLAG_COMMIT_REQ      = 1 << 4;

    private static final int PERSISTENT_STATE = STATE_MASK | FLAG_COMMIT_REQ;

    // non-persistent status flags
    private static final int FLAG_EXECUTE_DONE   = 1 << 5;
    private static final int FLAG_INSTALL_FAILED = 1 << 6;

    // non-persistent job flags
    private static final int FLAG_SEND_COMMIT_REQ           = 1 << 7; // to children
    private static final int FLAG_SEND_CHILD_EXECUTED       = 1 << 8; // to parents
    private static final int FLAG_SEND_CHILD_TERMINATED     = 1 << 9; // to parents
    private static final int FLAG_SEND_DEPENDENCY_EXECUTED  = 1 << 10; // to dependents

    private static final int SEND_FLAGS = Bits.intBitMask(7, 10);

    private static final int FLAG_DO_EXECUTE  = 1 << 11;

    private static final int DO_FLAGS = Bits.intBitMask(11, 11);

    @SuppressWarnings("unused")
    private static final int TASK_FLAGS = DO_FLAGS | SEND_FLAGS;

    private static final int FLAG_USER_THREAD = 1 << 31; // called from user thread; do not block

    TaskControllerImpl(final TaskParent parent, final TaskControllerImpl<?>[] dependencies, final Executable<T> executable, final ClassLoader classLoader) {
        this.parent = parent;
        this.dependencies = dependencies;
        this.executable = executable;
        this.classLoader = classLoader;
        state = STATE_NEW;
    }

    @Override
    public Transaction getTransaction() {
        return parent.getTransaction();
    }

    private AbstractTransaction getInternalTransaction() {
        return getAbstractTransaction(parent.getTransaction());
    }

    private static AbstractTransaction getAbstractTransaction(final Transaction transaction) {
        if (transaction instanceof BasicUpdateTransaction) return ((BasicUpdateTransaction)transaction).getDelegate();
        else return (BasicReadTransaction)transaction;
    }

    public T getResult() throws IllegalStateException {
        final T result = this.result;
        if (result == NO_RESULT) {
            throw MSCLogger.TASK.noTaskResult();
        }
        return result;
    }

    // ===================================================
    //   Private impl
    // ===================================================

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
            case STATE_NEW: {
                if (Bits.allAreSet(state, FLAG_INSTALL_FAILED)) {
                    return T_NEW_to_TERMINATED;
                } else {
                    return T_NEW_to_EXECUTE_WAIT;
                }
            }
            case STATE_EXECUTE_WAIT: {
                if (unexecutedDependencies == 0) {
                    return T_EXECUTE_WAIT_to_EXECUTE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE: {
                if (Bits.allAreSet(state, FLAG_EXECUTE_DONE)) {
                    return T_EXECUTE_to_EXECUTE_CHILDREN_WAIT;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE_CHILDREN_WAIT: {
                if (unexecutedChildren == 0) {
                    return T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE_DONE: {
                if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                    return T_EXECUTE_DONE_to_TERMINATE_WAIT;
                } else {
                    return T_NONE;
                }
            }
            case STATE_TERMINATE_WAIT: {
                if (unterminatedChildren == 0) {
                    return T_TERMINATE_WAIT_to_TERMINATED;
                } else {
                    return T_NONE;
                }
            }
            case STATE_TERMINATED: {
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
                case T_NEW_to_EXECUTE_WAIT: {
                    state = newState(STATE_EXECUTE_WAIT, state);
                    continue;
                }
                case T_EXECUTE_WAIT_to_EXECUTE: {
                    if (executable == null) {
                        state = newState(STATE_EXECUTE, state | FLAG_EXECUTE_DONE);
                        continue;
                    }
                    // not possible to go any farther
                    return newState(STATE_EXECUTE, state | FLAG_DO_EXECUTE);
                }
                case T_EXECUTE_to_EXECUTE_CHILDREN_WAIT: {
                    if (! dependents.isEmpty()) {
                        cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                        state = newState(STATE_EXECUTE_CHILDREN_WAIT, state | FLAG_SEND_DEPENDENCY_EXECUTED);
                    } else {
                        state = newState(STATE_EXECUTE_CHILDREN_WAIT, state);
                    }
                    continue;
                }
                case T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE: {
                    state = newState(STATE_EXECUTE_DONE, state | FLAG_SEND_CHILD_EXECUTED);
                    continue;
                }
                case T_EXECUTE_DONE_to_TERMINATE_WAIT: {
                    state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_COMMIT_REQ);
                    continue;
                }
                case T_TERMINATE_WAIT_to_TERMINATED: {
                    state = newState(STATE_TERMINATED, state | FLAG_SEND_CHILD_TERMINATED);
                    continue;
                }

                // exceptional cases

                case T_NEW_to_TERMINATED: {
                    // not possible to go any farther
                    return newState(STATE_TERMINATED, state);
                }
                default: throw new IllegalStateException();
            }
        }
    }

    private final Runnable executeTask = new Runnable() {
        public void run() {
            execute();
        }
    };

    private class ChildTerminatedTask implements Runnable {
        private final boolean userThread;

        private ChildTerminatedTask(final boolean userThread) {
            this.userThread = userThread;
        }

        public void run() {
            parent.childTerminated(userThread);
        }
    }

    private class SendCommitRequestTask implements Runnable {
        private final boolean userThread;

        private SendCommitRequestTask(final boolean userThread) {
            this.userThread = userThread;
        }

        public void run() {
            for (final TaskChild child : children) {
                child.childCommit(userThread);
            }
        }
    }

    private class ChildExecutedTask implements Runnable {
        private final boolean userThread;

        private ChildExecutedTask(final boolean userThread) {
            this.userThread = userThread;
        }

        public void run() {
            parent.childExecuted(userThread);
        }
    }

    private class DependencyExecutedTask implements Runnable {
        private final boolean userThread;
        private final TaskControllerImpl<?>[] dependents;

        private DependencyExecutedTask(final TaskControllerImpl<?>[] dependents, final boolean userThread) {
            this.dependents = dependents;
            this.userThread = userThread;
        }

        public void run() {
            for (TaskControllerImpl<?> dependent : dependents) {
                dependent.dependencyExecuted(userThread);
            }
        }
    }

    private void executeTasks(final int state) {
        final boolean userThread = Bits.allAreSet(state, FLAG_USER_THREAD);
        if (!Bits.allAreClear(state, DO_FLAGS)) {
            assert Bits.oneIsSet(state, DO_FLAGS);
            if (userThread) {
                if (Bits.allAreSet(state, FLAG_DO_EXECUTE)) {
                    safeExecute(new AsyncTask(FLAG_DO_EXECUTE));
                }
            } else {
                if (Bits.allAreSet(state, FLAG_DO_EXECUTE)) {
                    ThreadLocalExecutor.addTask(executeTask);
                }
            }
        }

        if (Bits.allAreSet(state, FLAG_SEND_CHILD_TERMINATED)) {
            ThreadLocalExecutor.addTask(new ChildTerminatedTask(userThread));
        }
        if (Bits.allAreSet(state, FLAG_SEND_COMMIT_REQ)) {
            ThreadLocalExecutor.addTask(new SendCommitRequestTask(userThread));
        }
        if (Bits.allAreSet(state, FLAG_SEND_CHILD_EXECUTED)) {
            ThreadLocalExecutor.addTask(new ChildExecutedTask(userThread));
        }
        final TaskControllerImpl<?>[] dependents = cachedDependents.get();
        cachedDependents.remove();
        if (dependents != null && Bits.allAreSet(state, FLAG_SEND_DEPENDENCY_EXECUTED)) {
            ThreadLocalExecutor.addTask(new DependencyExecutedTask(dependents, userThread));
        }
        ThreadLocalExecutor.executeTasks();
    }

    private void safeExecute(final Runnable command) {
        try {
            getInternalTransaction().getExecutor().execute(command);
        } catch (Throwable t) {
            MSCLogger.ROOT.runnableExecuteFailed(t, command);
        }
    }

    private static int newState(int sid, int state) {
        assert sid >= 0 && sid <= STATE_LAST;
        return sid & STATE_MASK | state & ~STATE_MASK;
    }

    private static int stateOf(int oldVal) {
        return oldVal & STATE_MASK;
    }

    private static boolean stateIsIn(int state, int sid1) {
        final int sid = stateOf(state);
        return sid == sid1;
    }

    private void execComplete(final T result) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD | FLAG_EXECUTE_DONE;
            if (stateOf(state) != STATE_EXECUTE) {
                throw MSCLogger.TASK.taskCannotComplete();
            }
            this.result = result;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void setClassLoader() {
        if (classLoader != null) {
            final Thread thread = Thread.currentThread();
            CL_HOLDER.set(thread.getContextClassLoader());
            thread.setContextClassLoader(classLoader);
        }
    }

    void unsetClassLoader() {
        if (classLoader != null) {
            final Thread thread = Thread.currentThread();
            final ClassLoader classLoader = CL_HOLDER.get();
            thread.setContextClassLoader(classLoader);
            CL_HOLDER.remove();
        }
    }

    void execute() {
        final ProblemReport problemReport = getTransaction().getReport();
        final Executable<T> exec = executable;
        if (exec != null) try {
            setClassLoader();
            exec.execute(new ExecuteContext<T>() {
                @Override
                public void complete(final T result) {
                    execComplete(result);
                }

                @Override
                public void complete() {
                    complete(null);
                }

                @Override
                public void addProblem(final Problem reason) {
                    problemReport.addProblem(reason);
                }

                @Override
                public void addProblem(final Problem.Severity severity, final String message) {
                    addProblem(new Problem(severity, message));
                }

                @Override
                public void addProblem(final Problem.Severity severity, final String message, final Throwable cause) {
                    addProblem(new Problem(severity, message, cause));
                }

                @Override
                public void addProblem(final String message, final Throwable cause) {
                    addProblem(new Problem(message, cause));
                }

                @Override
                public void addProblem(final String message) {
                    addProblem(new Problem(message));
                }

                @Override
                public void addProblem(final Throwable cause) {
                    addProblem(new Problem(cause));
                }

                @Override
                public <N> TaskBuilder<N> newTask(final Executable<N> task) throws IllegalStateException {
                    return new TaskBuilderImpl<>(getTransaction(), TaskControllerImpl.this, task);
                }
            });
        } catch (Throwable t) {
            MSCLogger.TASK.taskExecutionFailed(t, exec);
            problemReport.addProblem(new Problem(Problem.Severity.CRITICAL, t));
        } finally {
            unsetClassLoader();
        }
    }

    @Override
    public void childExecuted(final boolean userThread) {
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

    @Override
    public void childTerminated(final boolean userThread) {
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

    @Override
    public void childAdded(final TaskChild child, final boolean userThread) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateIsIn(state, STATE_EXECUTE)) {
                unexecutedChildren++;
                unterminatedChildren++;
                children.add((TaskControllerImpl<?>) child);
                if (userThread) state |= FLAG_USER_THREAD;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            } else {
                if (userThread) {
                    throw MSCLogger.TASK.cannotAddDepToTask();
                } else {
                    // todo log and ignore...
                    return;
                }
            }
        }
        executeTasks(state);
    }

    public void dependencyExecuted(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unexecutedDependencies--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    public void childCommit(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_COMMIT_REQ;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void dependentAdded(final TaskControllerImpl<?> dependent, final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        boolean dependencyDone = false;

        synchronized (this) {
            state = this.state;
            dependents.add(dependent);
            switch (stateOf(state)) {
                case STATE_EXECUTE_DONE:
                case STATE_EXECUTE_CHILDREN_WAIT:
                    dependencyDone = true;
            }
        }
        if (dependencyDone) {
            dependent.dependencyExecuted(userThread);
        }
    }

    void install() {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unexecutedDependencies = dependencies.length;
        }
        try {
            parent.childAdded(this, true);
        } catch (IllegalStateException e) {
            synchronized (this) {
                state = this.state | FLAG_USER_THREAD | FLAG_INSTALL_FAILED;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            }
            executeTasks(state);
            throw e;
        }
        for (final TaskControllerImpl<?> dependency : dependencies) {
            try {
                dependency.dependentAdded(this, true);
            } catch (IllegalStateException e) {
                parent.childTerminated(true);
                synchronized (this) {
                    state = this.state | FLAG_USER_THREAD | FLAG_INSTALL_FAILED;
                    state = transition(state);
                    this.state = state & PERSISTENT_STATE;
                }
                executeTasks(state);
                throw e;
            }
        }
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
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
            return TaskControllerImpl.this + ".AsyncTask@" + System.identityHashCode(this);
        }
    }
}
