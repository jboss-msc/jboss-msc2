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
import java.util.Set;

import static java.lang.Thread.holdsLock;

/**
 * A controller for an installed task.
 *
 * The following defines the state machine for this class.
 * <pre>
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
 *  +---------------+
 *  |               |
 *  |  EXECUTE_DONE |
 *  |               |
 *  +-------+-------+
 * </pre>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class TaskControllerImpl<T> implements TaskController<T> {

    private static final Object NO_RESULT = new Object();

    private static final ThreadLocal<ClassLoader> CL_HOLDER = new ThreadLocal<>();

    private final AbstractTransaction txn;
    private final Executable<T> executable;
    private final ClassLoader classLoader;
    private final ArrayList<TaskControllerImpl<?>> dependents = new ArrayList<>();

    private int state;
    private int unexecutedDependencies;

    @SuppressWarnings("unchecked")
    private volatile T result = (T) NO_RESULT;

    private static final ThreadLocal<TaskControllerImpl<?>[]> cachedDependents = new ThreadLocal<>();

    private static final int STATE_MASK         = 0x3;
    private static final int STATE_EXECUTE_WAIT = 0;
    private static final int STATE_EXECUTE      = 1;
    private static final int STATE_EXECUTE_DONE = 2;

    private static final int T_NONE = 0;
    private static final int T_EXECUTE_WAIT_to_EXECUTE = 1;
    private static final int T_EXECUTE_to_EXECUTE_DONE = 2;

    // non-persistent status flags
    private static final int FLAG_EXECUTE_DONE = 1 << 3;

    // non-persistent job flags
    private static final int FLAG_SEND_TASK_EXECUTED       = 1 << 4; // to transaction
    private static final int FLAG_SEND_DEPENDENCY_EXECUTED = 1 << 5; // to dependents

    private static final int SEND_FLAGS = Bits.intBitMask(4, 5);

    private static final int FLAG_DO_EXECUTE = 1 << 6;

    private static final int DO_FLAGS = Bits.intBitMask(6, 6);

    @SuppressWarnings("unused")
    private static final int TASK_FLAGS = DO_FLAGS | SEND_FLAGS;

    private static final int FLAG_USER_THREAD = 1 << 31; // called from user thread; do not block

    TaskControllerImpl(final AbstractTransaction txn, final Executable<T> executable, final ClassLoader classLoader) {
        this.txn = txn;
        this.executable = executable;
        this.classLoader = classLoader;
    }

    @Override
    public Transaction getTransaction() {
        return txn.wrappingTxn;
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
            case STATE_EXECUTE_WAIT: {
                if (unexecutedDependencies == 0) {
                    return T_EXECUTE_WAIT_to_EXECUTE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE: {
                if (Bits.allAreSet(state, FLAG_EXECUTE_DONE)) {
                    return T_EXECUTE_to_EXECUTE_DONE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE_DONE: {
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
                case T_EXECUTE_WAIT_to_EXECUTE: {
                    if (executable == null) {
                        state = newState(STATE_EXECUTE, state | FLAG_EXECUTE_DONE);
                        continue;
                    }
                    return newState(STATE_EXECUTE, state | FLAG_DO_EXECUTE);
                }
                case T_EXECUTE_to_EXECUTE_DONE: {
                    if (! dependents.isEmpty()) {
                        cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                        state = newState(STATE_EXECUTE_DONE, state | FLAG_SEND_DEPENDENCY_EXECUTED | FLAG_SEND_TASK_EXECUTED);
                    } else {
                        state = newState(STATE_EXECUTE_DONE, state | FLAG_SEND_TASK_EXECUTED);
                    }
                    continue;
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

    private class TaskExecuted implements Runnable {
        private final boolean userThread;

        private TaskExecuted(final boolean userThread) {
            this.userThread = userThread;
        }

        public void run() {
            txn.taskExecuted(userThread);
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
        if (Bits.allAreSet(state, FLAG_SEND_TASK_EXECUTED)) {
            ThreadLocalExecutor.addTask(new TaskExecuted(userThread));
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
            txn.getExecutor().execute(command);
        } catch (Throwable t) {
            MSCLogger.ROOT.runnableExecuteFailed(t, command);
        }
    }

    private static int newState(int sid, int state) {
        assert sid >= 0 && sid <= STATE_EXECUTE_DONE;
        return sid & STATE_MASK | state & ~STATE_MASK;
    }

    private static int stateOf(int oldVal) {
        return oldVal & STATE_MASK;
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
            this.state = state & STATE_MASK;
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
                    return txn.getTaskFactory().newTask(task);
                }
            });
        } catch (Throwable t) {
            MSCLogger.TASK.taskExecutionFailed(t, exec);
            problemReport.addProblem(new Problem(Problem.Severity.CRITICAL, t));
        } finally {
            unsetClassLoader();
        }
    }

    public void dependencyExecuted(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unexecutedDependencies--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & STATE_MASK;
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
            if (stateOf(state) == STATE_EXECUTE_DONE) {
                dependencyDone = true;
            }
        }
        if (dependencyDone) {
            dependent.dependencyExecuted(userThread);
        }
    }

    void install(final Set<TaskControllerImpl<?>> dependencies) {
        assert ! holdsLock(this);
        txn.taskAdded(true);
        synchronized (this) {
            unexecutedDependencies = dependencies.size();
        }
        for (final TaskControllerImpl<?> dependency : dependencies) {
            dependency.dependentAdded(this, true);
        }
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & STATE_MASK;
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
