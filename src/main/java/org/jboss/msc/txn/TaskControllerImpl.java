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
import org.jboss.msc.problem.Problem;
import org.jboss.msc.problem.ProblemReport;

import static java.lang.Thread.holdsLock;

/**
 * A controller for an installed task.
 *
 * The following defines the state machine for this class.
 * <pre>
 *  +--------------+     +---------+     +--------------+
 *  | EXECUTE_WAIT |---->| EXECUTE |---->| EXECUTE_DONE |
 *  +------+-------+     +---------+     +--------------+
 * </pre>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class TaskControllerImpl<T> implements TaskController<T> {

    private static final Object NO_RESULT = new Object();

    private final AbstractTransaction txn;
    private final Executable<T> executable;
    private byte state;

    @SuppressWarnings("unchecked")
    private volatile T result = (T) NO_RESULT;

    private static final byte STATE_MASK         = 0x3;
    private static final byte STATE_EXECUTE_WAIT = 0;
    private static final byte STATE_EXECUTE      = 1;
    private static final byte STATE_EXECUTE_DONE = 2;

    private static final byte FLAG_SEND_TASK_EXECUTED = 1 << 3;
    private static final byte FLAG_DO_EXECUTE         = 1 << 4;

    TaskControllerImpl(final AbstractTransaction txn, final Executable<T> executable) {
        this.txn = txn;
        this.executable = executable;
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

    private int transition(int state) {
        assert holdsLock(this);
        switch (stateOf(state)) {
            case STATE_EXECUTE_WAIT: return newState(STATE_EXECUTE, FLAG_DO_EXECUTE);
            case STATE_EXECUTE: return newState(STATE_EXECUTE_DONE, FLAG_SEND_TASK_EXECUTED);
            case STATE_EXECUTE_DONE: return state;
            default: throw new IllegalStateException();
        }
    }

    private void executeTasks(final int state) {
        if (Bits.allAreSet(state, FLAG_DO_EXECUTE)) {
            txn.safeExecute(new Runnable() { public void run() { execute(); }});
        }
        if (Bits.allAreSet(state, FLAG_SEND_TASK_EXECUTED)) {
            ThreadLocalExecutor.addTask(new Runnable() { public void run() { txn.taskExecuted(); }});
        }
        ThreadLocalExecutor.executeTasks();
    }

    private static int newState(int sid, int flags) {
        assert sid >= 0 && sid <= STATE_EXECUTE_DONE;
        return sid & STATE_MASK | flags & ~STATE_MASK;
    }

    private static int stateOf(int oldVal) {
        return oldVal & STATE_MASK;
    }

    private void execComplete(final T result) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateOf(state) != STATE_EXECUTE) {
                throw MSCLogger.TASK.taskCannotComplete();
            }
            this.result = result;
            state = transition(state);
            this.state = (byte) (state & STATE_MASK);
        }
        executeTasks(state);
    }

    void execute() {
        final ProblemReport problemReport = getTransaction().getReport();
        final Executable<T> exec = executable;
        if (exec != null) try {
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
            });
        } catch (Throwable t) {
            MSCLogger.TASK.taskExecutionFailed(t, exec);
            problemReport.addProblem(new Problem(Problem.Severity.CRITICAL, t));
        }
    }

    void install() {
        assert ! holdsLock(this);
        txn.taskAdded();
        int state;
        synchronized (this) {
            state = transition(this.state);
            this.state = (byte) (state & STATE_MASK);
        }
        executeTasks(state);
    }
}
