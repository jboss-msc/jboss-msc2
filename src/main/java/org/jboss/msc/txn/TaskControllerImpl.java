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

import static java.lang.Thread.holdsLock;
import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.ArrayList;

import org.jboss.msc._private.MSCLogger;

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
 *  |               |       ROLLBACK_REQ or CANCEL_REQ
 *  |  EXECUTE_WAIT +------------------------------------------+
 *  |               |                                          |
 *  +-------+-------+                                          |
 *          |                                                  |
 *          |                                                  |
 *          v                                                  |
 *  +---------------+                                          |
 *  |               |         SELF_CANCEL_REQ                  |
 *  |    EXECUTE    +---------------------------------+        |
 *  |               |                                 |        |
 *  +-------+-------+                                 |        |
 *          |                                         |        |
 *          |                                         |        |
 *          v                                         |        |
 *  +-----------------------+                         |        |
 *  |                       |                         |        |
 *  | EXECUTE_CHILDREN_WAIT +----------------+        |        |
 *  |                       |                |        |        |
 *  +-------+---------------+                |        |        |
 *          |                                |        |        |
 *          |                                |        |        |
 *          v                                |        |        |
 *  +---------------+                        |        |        |
 *  |               |                        |        |        |
 *  |  EXECUTE_DONE +-------------------+    |        |        |
 *  |               |                   |    |        |        |
 *  +-------+-------+                   |    |        |        |
 *          |                           |    |        |        |
 *          |                           |    |        |        |
 *          v                           |    |        |        |
 *  +---------------+                   |    |        |        |
 *  |               |                   |    |        |        |
 *  |    VALIDATE   |                   |    |        |        |
 *  |               |                   |    |        |        |
 *  +-------+-------+                   |    |        |        |
 *          |                           |    |        |        |
 *          |                           |    |        |        |
 *          v                           |    |        |        |
 *  +------------------------+          |    |        |        |
 *  |                        |          |    |        |        |
 *  | VALIDATE_CHILDREN_WAIT +-----+    |    |        |        |
 *  |                        |     |    |    |        |        |
 *  +-------+----------------+     |    |    |        |        |
 *          |                      |    |    |        |        |
 *          |                      |    |    |        |        |
 *          v                      |    |    |        |        |
 *  +---------------+              |    |    |        |        |
 *  |               |              |    |    |        |        |
 *  | VALIDATE_DONE +---------+    |    |    |        |        |
 *  |               |         |    |    |    |        |        |
 *  +-------+-------+                                 |        |
 *          |            ROLLBACK_REQ or CANCEL_REQ   |        |
 *          |                                         |        |
 *          |                 |    |    |    |        |        |
 *          |                 v    v    v    v        |        |
 *          |             +----------------------+    |        |
 *          |             |                      |    |        |
 *          |             |    ROLLBACK_WAIT     |    |        |
 *          |             |                      |    |        |
 *          |             +-----------+----------+    |        |
 *          |                         |               |        |
 *          | COMMIT_REQ              |               |        |
 *          |                         v               |        |
 *          |             +----------------------+    |        |
 *          |             |                      |    |        |
 *          |             |       ROLLBACK       |    |        |
 *          |             |                      |    |        |
 *          |             +-----------+----------+    |        |
 *          |                         |               |        |
 *          |                         |               |        |
 *          v                         v               v        v
 *  +------------------------------------------------------------+
 *  |                                                            |
 *  |                       TERMINATE_WAIT                       |
 *  |                                                            |
 *  +-----------------------------+------------------------------+
 *                                |
 *                                |
 *                                v
 *  +------------------------------------------------------------+
 *  |                                                            |
 *  |                         TERMINATED                         |
 *  |                                                            |
 *  +------------------------------------------------------------+
 * </pre>
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
final class TaskControllerImpl<T> implements TaskController<T>, TaskParent, TaskChild {

    private static final Object NO_RESULT = new Object();

    private static final ThreadLocal<ClassLoader> CL_HOLDER = new ThreadLocal<>();

    private final DelegatingTaskParent parent;
    private TaskParent adopter;
    private final TaskControllerImpl<?>[] dependencies;
    private final Executable<T> executable;
    private final Revertible revertible;
    private final Validatable validatable;
    private final ClassLoader classLoader;
    private final ArrayList<TaskControllerImpl<?>> dependents = new ArrayList<>();
    private final ArrayList<TaskControllerImpl<?>> children = new ArrayList<>();

    private int state;
    private int unfinishedDependencies;
    private int unfinishedChildren;
    private int unvalidatedChildren;
    private int unterminatedChildren;
    private int unterminatedDependents;

    private boolean sendChildExecuted;
    private boolean sendChildValidated;

    @SuppressWarnings("unchecked")
    private volatile T result = (T) NO_RESULT;

    private static final ThreadLocal<TaskControllerImpl<?>[]> cachedDependents = new ThreadLocal<>();

    private static final int STATE_MASK        = 0xF;

    private static final int STATE_NEW                    = 0;
    private static final int STATE_EXECUTE_WAIT           = 1;
    private static final int STATE_EXECUTE                = 2;
    private static final int STATE_EXECUTE_CHILDREN_WAIT  = 3;
    private static final int STATE_EXECUTE_DONE           = 4;
    private static final int STATE_VALIDATE               = 5;
    private static final int STATE_VALIDATE_CHILDREN_WAIT = 6;
    private static final int STATE_VALIDATE_DONE          = 7;
    private static final int STATE_ROLLBACK_WAIT          = 8;
    private static final int STATE_ROLLBACK               = 9;
    private static final int STATE_TERMINATE_WAIT         = 10;
    private static final int STATE_TERMINATED             = 11;
    private static final int STATE_LAST = STATE_TERMINATED;

    private static final int T_NONE = 0;

    private static final int T_NEW_to_EXECUTE_WAIT = 1;
    private static final int T_NEW_to_TERMINATED = 2;

    private static final int T_EXECUTE_WAIT_to_TERMINATE_WAIT = 3;
    private static final int T_EXECUTE_WAIT_to_EXECUTE = 4;

    private static final int T_EXECUTE_to_TERMINATE_WAIT = 5;
    private static final int T_EXECUTE_to_EXECUTE_CHILDREN_WAIT = 6;

    private static final int T_EXECUTE_CHILDREN_WAIT_to_ROLLBACK_WAIT = 7;
    private static final int T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE = 8;

    private static final int T_EXECUTE_DONE_to_ROLLBACK_WAIT = 9;
    private static final int T_EXECUTE_DONE_to_VALIDATE = 10;

    private static final int T_VALIDATE_to_VALIDATE_CHILDREN_WAIT = 11;

    private static final int T_VALIDATE_CHILDREN_WAIT_to_ROLLBACK_WAIT = 12;
    private static final int T_VALIDATE_CHILDREN_WAIT_to_VALIDATE_DONE = 13;

    private static final int T_VALIDATE_DONE_to_ROLLBACK_WAIT = 14;
    private static final int T_VALIDATE_DONE_to_TERMINATE_WAIT = 15;

    private static final int T_ROLLBACK_WAIT_to_ROLLBACK = 16;

    private static final int T_ROLLBACK_to_TERMINATE_WAIT = 17;

    private static final int T_TERMINATE_WAIT_to_TERMINATED = 18;

    /**
     * A cancel request, due to rollback or dependency failure.
     */
    private static final int FLAG_CANCEL_REQ      = 1 << 4;
    private static final int FLAG_SELF_CANCEL_REQ = 1 << 5;
    private static final int FLAG_ROLLBACK_REQ    = 1 << 6;
    private static final int FLAG_VALIDATE_REQ    = 1 << 7;
    private static final int FLAG_COMMIT_REQ      = 1 << 8;

    private static final int PERSISTENT_STATE = STATE_MASK | FLAG_CANCEL_REQ | FLAG_ROLLBACK_REQ | FLAG_VALIDATE_REQ | FLAG_COMMIT_REQ;

    // non-persistent status flags
    private static final int FLAG_EXECUTE_DONE   = 1 << 9;
    private static final int FLAG_VALIDATE_DONE  = 1 << 10;
    private static final int FLAG_ROLLBACK_DONE  = 1 << 11;
    private static final int FLAG_INSTALL_FAILED = 1 << 12;

    // non-persistent job flags
    private static final int FLAG_SEND_CHILD_DONE          = 1 << 13; // to parents
    private static final int FLAG_SEND_DEPENDENCY_DONE     = 1 << 14; // to dependents
    private static final int FLAG_SEND_VALIDATE_REQ        = 1 << 15; // to children
    private static final int FLAG_SEND_CHILD_VALIDATE_DONE = 1 << 16; // to parents
    private static final int FLAG_SEND_CHILD_TERMINATED    = 1 << 17; // to parents
    private static final int FLAG_SEND_TERMINATED          = 1 << 18; // to dependencies
    private static final int FLAG_SEND_CANCEL_REQUESTED    = 1 << 19; // to transaction
    private static final int FLAG_SEND_CANCELLED           = 1 << 20; // to transaction
    private static final int FLAG_SEND_RENOUNCE_CHILDREN   = 1 << 21; // to transaction
    private static final int FLAG_SEND_ROLLBACK_REQ        = 1 << 22; // to children
    private static final int FLAG_SEND_COMMIT_REQ          = 1 << 23; // to children
    private static final int FLAG_SEND_CANCEL_DEPENDENTS   = 1 << 24; // to dependents

    private static final int SEND_FLAGS = Bits.intBitMask(13, 24);

    private static final int FLAG_DO_EXECUTE  = 1 << 25;
    private static final int FLAG_DO_VALIDATE = 1 << 26;
    private static final int FLAG_DO_ROLLBACK = 1 << 27;

    private static final int DO_FLAGS = Bits.intBitMask(25, 27);

    @SuppressWarnings("unused")
    private static final int TASK_FLAGS = DO_FLAGS | SEND_FLAGS;

    private static final int FLAG_USER_THREAD = 1 << 31; // called from user thread; do not block

    private static class DelegatingTaskParent implements TaskParent {

        private TaskParent delegate;

        private DelegatingTaskParent(final TaskParent delegate) {
            this.delegate = delegate;
        }

        private synchronized void setDelegate(final TaskParent delegate) {
            this.delegate = delegate;
        }

        private synchronized TaskParent getDelegate() {
            return delegate;
        }

        @Override
        public void childExecuted(final boolean userThread) {
            getDelegate().childExecuted(userThread);
        }

        @Override
        public void childValidated(final boolean userThread) {
            getDelegate().childValidated(userThread);
        }

        @Override
        public void childTerminated(final boolean userThread) {
            getDelegate().childTerminated(userThread);
        }

        @Override
        public void childAdded(final TaskChild child, final boolean userThread) throws InvalidTransactionStateException {
            getDelegate().childAdded(child, userThread);
        }

        @Override
        public Transaction getTransaction() {
            return getDelegate().getTransaction();
        }

    }

    TaskControllerImpl(final TaskParent parent, final TaskControllerImpl<?>[] dependencies, final Executable<T> executable, final Revertible revertible, final Validatable validatable, final ClassLoader classLoader) {
        this.parent = new DelegatingTaskParent(parent);
        this.dependencies = dependencies;
        this.executable = executable;
        this.revertible = revertible;
        this.validatable = validatable;
        this.classLoader = classLoader;
        state = STATE_NEW;
    }

    @Override
    public Transaction getTransaction() {
        return parent.getTransaction();
    }

    public T getResult() throws IllegalStateException {
        final T result = this.result;
        if (result == NO_RESULT) {
            throw new IllegalStateException("No result is available");
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
                if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ)) {
                    return T_EXECUTE_WAIT_to_TERMINATE_WAIT;
                } else if (unfinishedDependencies == 0) {
                    return T_EXECUTE_WAIT_to_EXECUTE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE: {
                if (Bits.allAreSet(state, FLAG_SELF_CANCEL_REQ)) {
                    return T_EXECUTE_to_TERMINATE_WAIT;
                } else if (Bits.allAreSet(state, FLAG_EXECUTE_DONE)) {
                    return T_EXECUTE_to_EXECUTE_CHILDREN_WAIT;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE_CHILDREN_WAIT: {
                if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ)) {
                    return T_EXECUTE_CHILDREN_WAIT_to_ROLLBACK_WAIT;
                } else if (unfinishedChildren == 0) {
                    return T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_EXECUTE_DONE: {
                if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ)) {
                    return T_EXECUTE_DONE_to_ROLLBACK_WAIT;
                } else if (Bits.allAreSet(state, FLAG_VALIDATE_REQ)) {
                    return T_EXECUTE_DONE_to_VALIDATE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_VALIDATE: {
                if (Bits.allAreSet(state, FLAG_VALIDATE_DONE)) {
                    return T_VALIDATE_to_VALIDATE_CHILDREN_WAIT;
                } else {
                    return T_NONE;
                }
            }
            case STATE_VALIDATE_CHILDREN_WAIT: {
                if (Bits.anyAreSet(state, FLAG_CANCEL_REQ)) {
                    return T_VALIDATE_CHILDREN_WAIT_to_ROLLBACK_WAIT;
                } else if (unvalidatedChildren == 0) {
                    return T_VALIDATE_CHILDREN_WAIT_to_VALIDATE_DONE;
                } else {
                    return T_NONE;
                }
            }
            case STATE_VALIDATE_DONE: {
                if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ)) {
                    return T_VALIDATE_DONE_to_ROLLBACK_WAIT;
                } else if (Bits.allAreSet(state, FLAG_COMMIT_REQ)) {
                    return T_VALIDATE_DONE_to_TERMINATE_WAIT;
                } else {
                    return T_NONE;
                }
            }
            case STATE_ROLLBACK_WAIT: {
                if (unterminatedDependents == 0 && (Bits.allAreSet(state, FLAG_CANCEL_REQ) || unterminatedChildren == 0)) {
                    return T_ROLLBACK_WAIT_to_ROLLBACK;
                } else {
                    return T_NONE;
                }
            }
            case STATE_ROLLBACK: {
                if (Bits.allAreSet(state, FLAG_ROLLBACK_DONE)) {
                    return T_ROLLBACK_to_TERMINATE_WAIT;
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
                        state = newState(STATE_EXECUTE_CHILDREN_WAIT, state | FLAG_SEND_DEPENDENCY_DONE);
                    } else {
                        state = newState(STATE_EXECUTE_CHILDREN_WAIT, state);
                    }
                    continue;
                }
                case T_EXECUTE_CHILDREN_WAIT_to_EXECUTE_DONE: {
                    state = newState(STATE_EXECUTE_DONE, state | FLAG_SEND_CHILD_DONE);
                    continue;
                }
                case T_EXECUTE_DONE_to_VALIDATE: {
                    if (validatable == null) {
                        state = newState(STATE_VALIDATE, state | FLAG_VALIDATE_DONE);
                        continue;
                    }
                    // not possible to go any farther
                    return newState(STATE_VALIDATE, state | FLAG_DO_VALIDATE);
                }
                case T_VALIDATE_to_VALIDATE_CHILDREN_WAIT: {
                    state = newState(STATE_VALIDATE_CHILDREN_WAIT, state | FLAG_SEND_VALIDATE_REQ);
                    continue;
                }
                case T_VALIDATE_CHILDREN_WAIT_to_VALIDATE_DONE: {
                    state = newState(STATE_VALIDATE_DONE, state | FLAG_SEND_CHILD_VALIDATE_DONE);
                    continue;
                }
                case T_VALIDATE_DONE_to_TERMINATE_WAIT: {
                    state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_COMMIT_REQ);
                    continue;
                }
                case T_TERMINATE_WAIT_to_TERMINATED: {
                    if (Bits.allAreSet(state, FLAG_CANCEL_REQ)) {
                        state = newState(STATE_TERMINATED, state | FLAG_SEND_CHILD_TERMINATED | FLAG_SEND_TERMINATED | FLAG_SEND_CANCELLED);
                    } else {
                        state = newState(STATE_TERMINATED, state | FLAG_SEND_CHILD_TERMINATED | FLAG_SEND_TERMINATED);
                    }
                    continue;
                }

                // exceptional cases

                case T_NEW_to_TERMINATED: {
                    // not possible to go any farther
                    if (Bits.allAreSet(state, FLAG_CANCEL_REQ)) {
                        return newState(STATE_TERMINATED, state | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE | FLAG_SEND_CHILD_TERMINATED | FLAG_SEND_CANCELLED);
                    } else {
                        return newState(STATE_TERMINATED, state | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE | FLAG_SEND_CHILD_TERMINATED);
                    }
                }
                case T_EXECUTE_WAIT_to_TERMINATE_WAIT: {
                    if (! dependents.isEmpty()) {
                        cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE);
                    } else {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE);
                    }
                    continue;
                }
                case T_EXECUTE_to_TERMINATE_WAIT: {
                    if (! dependents.isEmpty()) {
                        cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE);
                    } else {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CHILD_DONE | FLAG_SEND_CHILD_VALIDATE_DONE);
                    }
                    if (Bits.allAreSet(state, FLAG_CANCEL_REQ) && children.size() > 0) {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_RENOUNCE_CHILDREN);
                    }
                    continue;
                }
                case T_EXECUTE_CHILDREN_WAIT_to_ROLLBACK_WAIT: {
                    if (Bits.anyAreSet(state, FLAG_CANCEL_REQ)) {
                        if (! dependents.isEmpty()) {
                            cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                            state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS);
                        } else {
                            state = newState(STATE_ROLLBACK_WAIT, state);
                        }
                    }
                    if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ)) {
                        state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_ROLLBACK_REQ);
                    }
                    sendChildExecuted = true;
                    sendChildValidated = true;
                    continue;
                }
                case T_EXECUTE_DONE_to_ROLLBACK_WAIT: {
                    if (Bits.anyAreSet(state, FLAG_CANCEL_REQ)) {
                        if (! dependents.isEmpty()) {
                            cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                            state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS);
                        } else {
                            state = newState(STATE_ROLLBACK_WAIT, state);
                        }
                    }
                    if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ)) {
                        state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_ROLLBACK_REQ);
                    }
                    sendChildValidated = true;
                    continue;
                }
                case T_VALIDATE_CHILDREN_WAIT_to_ROLLBACK_WAIT: {
                    if (Bits.anyAreSet(state, FLAG_CANCEL_REQ)) {
                        if (! dependents.isEmpty()) {
                            cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                            state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS);
                        } else {
                            state = newState(STATE_ROLLBACK_WAIT, state);
                        }
                    }
                    sendChildValidated = true;
                    continue;
                }
                case T_VALIDATE_DONE_to_ROLLBACK_WAIT: {
                    if (Bits.anyAreSet(state, FLAG_CANCEL_REQ)) {
                        if (! dependents.isEmpty()) {
                            cachedDependents.set(dependents.toArray(new TaskControllerImpl[dependents.size()]));
                            state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_CANCEL_DEPENDENTS);
                        } else {
                            state = newState(STATE_ROLLBACK_WAIT, state);
                        }
                    }
                    if (Bits.anyAreSet(state, FLAG_ROLLBACK_REQ)) {
                        state = newState(STATE_ROLLBACK_WAIT, state | FLAG_SEND_ROLLBACK_REQ);
                    }
                    continue;
                }
                case T_ROLLBACK_WAIT_to_ROLLBACK: {
                    if (revertible == null) {
                        state = newState(STATE_ROLLBACK, state | FLAG_ROLLBACK_DONE);
                        continue;
                    }
                    // not possible to go any farther
                    return newState(STATE_ROLLBACK, state | FLAG_DO_ROLLBACK);
                }
                case T_ROLLBACK_to_TERMINATE_WAIT: {
                    if (Bits.allAreSet(state, FLAG_CANCEL_REQ) && children.size() > 0) {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_RENOUNCE_CHILDREN);
                    } else {
                        state = newState(STATE_TERMINATE_WAIT, state);
                    }
                    if (sendChildExecuted) {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CHILD_DONE);
                    }
                    if (sendChildValidated) {
                        state = newState(STATE_TERMINATE_WAIT, state | FLAG_SEND_CHILD_VALIDATE_DONE);
                    }
                    continue;
                }
                default: throw new IllegalStateException();
            }
        }
    }

    private void executeTasks(final int state) {
        final boolean userThread = Bits.allAreSet(state, FLAG_USER_THREAD);
        if (Bits.allAreSet(state,  FLAG_SEND_CANCEL_REQUESTED)) {
            getTransaction().childCancelRequested(userThread);
        }
        if (Bits.allAreSet(state, FLAG_SEND_RENOUNCE_CHILDREN)) {
            renounceChildren(userThread);
        }
        final TaskControllerImpl<?>[] dependents = cachedDependents.get();
        cachedDependents.remove();
        if (dependents != null && Bits.allAreSet(state, FLAG_SEND_CANCEL_DEPENDENTS)) {
            for (TaskControllerImpl<?> dependent : dependents) {
                dependent.forceCancel(userThread);
            }
        }
        if (dependents != null && Bits.allAreSet(state, FLAG_SEND_DEPENDENCY_DONE)) {
            for (TaskControllerImpl<?> dependent : dependents) {
                dependent.dependencyExecutionComplete(userThread);
            }
        }
        if (Bits.allAreSet(state, FLAG_SEND_CHILD_DONE)) {
            parent.childExecuted(userThread);
        }
        if (Bits.allAreSet(state, FLAG_SEND_VALIDATE_REQ)) {
            for (TaskChild child : children) {
                child.childInitiateValidate(userThread);
            }
        }
        if (Bits.allAreSet(state, FLAG_SEND_ROLLBACK_REQ)) {
            for (TaskChild child : children) {
                child.childInitiateRollback(userThread);
            }
        }
        if (Bits.allAreSet(state, FLAG_SEND_COMMIT_REQ)) {
            for (TaskChild child : children) {
                child.childInitiateCommit(userThread);
            }
        }
        if (Bits.allAreSet(state, FLAG_SEND_CHILD_VALIDATE_DONE)) {
            parent.childValidated(userThread);
        }
        if (Bits.allAreSet(state, FLAG_SEND_CHILD_TERMINATED)) {
            parent.childTerminated(userThread);
        }
        if (Bits.allAreSet(state, FLAG_SEND_CANCELLED)) {
            getTransaction().childCancelled(userThread);
        }
        if (Bits.allAreSet(state, FLAG_SEND_TERMINATED)) {
            for (TaskControllerImpl<?> dependency : dependencies) {
                dependency.dependentTerminated(userThread);
            }
        }

        if (Bits.allAreClear(state, DO_FLAGS)) return;

        assert Bits.oneIsSet(state, DO_FLAGS);

        if (userThread) {
            if (Bits.allAreSet(state, FLAG_DO_EXECUTE)) {
                safeExecute(new AsyncTask(FLAG_DO_EXECUTE));
            }
            if (Bits.allAreSet(state, FLAG_DO_VALIDATE)) {
                safeExecute(new AsyncTask(FLAG_DO_VALIDATE));
            }
            if (Bits.allAreSet(state, FLAG_DO_ROLLBACK)) {
                safeExecute(new AsyncTask(FLAG_DO_ROLLBACK));
            }
        } else {
            if (Bits.allAreSet(state, FLAG_DO_EXECUTE)) {
                execute();
            }
            if (Bits.allAreSet(state, FLAG_DO_VALIDATE)) {
                validate();
            }
            if (Bits.allAreSet(state, FLAG_DO_ROLLBACK)) {
                rollback();
            }
        }
    }

    private void renounceChildren(final boolean userThread) {
        assert !holdsLock(this);
        int state;
        final int unfinishedChildren;
        final int unvalidatedChildren;
        final int unterminatedChildren;
        final ArrayList<TaskControllerImpl<?>> children;
        synchronized (this) {
            unfinishedChildren = this.unfinishedChildren;
            unvalidatedChildren = this.unvalidatedChildren;
            unterminatedChildren = this.unterminatedChildren;
            children = this.children;
            adopter = getTransaction().topParent;
            for (final TaskControllerImpl<?> child : this.children) {
                child.parent.setDelegate(adopter);
            }
            this.unfinishedChildren = 0;
            this.unvalidatedChildren = 0;
            this.unterminatedChildren = 0;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        getTransaction().adoptGrandchildren(children, userThread, unfinishedChildren, unvalidatedChildren, unterminatedChildren);
        executeTasks(state);
    }

    private void safeExecute(final Runnable command) {
        try {
            getTransaction().getExecutor().execute(command);
        } catch (Throwable t) {
            MSCLogger.ROOT.runnableExecuteFailed(t, command);
        }
    }

    void forceCancel() {
        forceCancel(false);
    }

    private void forceCancel(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            if (Bits.anyAreSet(this.state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ)) return; // idempotent
            state = this.state | FLAG_CANCEL_REQ | FLAG_SEND_CANCEL_REQUESTED;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void dependentTerminated(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unterminatedDependents--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
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

    private static boolean stateIsIn(int state, int sid1, int sid2, int sid3, int sid4, int sid5, int sid6) {
        final int sid = stateOf(state);
        return sid == sid1 || sid == sid2 || sid == sid3 || sid == sid4 || sid == sid5 || sid == sid6;
    }

    private void execComplete(final T result) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD | FLAG_EXECUTE_DONE;
            if (stateOf(state) != STATE_EXECUTE) {
                throw new IllegalStateException("Task may not be completed now");
            }
            this.result = result;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void execCancelled() {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (stateOf(state) != STATE_EXECUTE) {
                throw new IllegalStateException("Task may not be cancelled now");
            }
            if (Bits.allAreClear(state, FLAG_CANCEL_REQ)) state |= FLAG_SEND_CANCEL_REQUESTED; 
            state |= FLAG_USER_THREAD | FLAG_CANCEL_REQ | FLAG_SELF_CANCEL_REQ;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    private void rollbackComplete() {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD | FLAG_ROLLBACK_DONE;
            if (stateOf(state) != STATE_ROLLBACK) {
                throw new IllegalStateException("Task may not be reverted now");
            }
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    void validate() {
        final ProblemReport problemReport = getTransaction().getProblemReport();
        final Validatable validatable = this.validatable;
        if (validatable != null) try {
            setClassLoader();
            validatable.validate(new ValidateContext() {
                public void addProblem(final Problem reason) {
                    problemReport.addProblem(reason);
                }

                public void addProblem(final Problem.Severity severity, final String message) {
                    addProblem(new Problem(TaskControllerImpl.this, message, severity));
                }

                public void addProblem(final Problem.Severity severity, final String message, final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, message, cause, severity));
                }

                public void addProblem(final String message, final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, message, cause));
                }

                public void addProblem(final String message) {
                    addProblem(new Problem(TaskControllerImpl.this, message));
                }

                public void addProblem(final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, cause));
                }

                public void complete() {
                    validateComplete();
                }
            });
        } catch (Throwable t) {
            MSCLogger.TASK.taskValidationFailed(t, validatable);
        } finally {
            unsetClassLoader();
        }
    }

    void validateComplete() {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_USER_THREAD | FLAG_VALIDATE_DONE;
            if (stateOf(state) != STATE_VALIDATE) {
                throw new IllegalStateException("Task may not be completed now");
            }
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

    void rollback() {
        final Revertible rev = revertible;
        if (rev != null) try {
            setClassLoader();
            rev.rollback(new RollbackContext() {
                public void complete() {
                    rollbackComplete();
                }
            });
        } catch (Throwable t) {
            MSCLogger.TASK.taskRollbackFailed(t, rev);
        } finally {
            unsetClassLoader();
        }
    }

    void execute() {
        final ProblemReport problemReport = getTransaction().getProblemReport();
        final Executable<T> exec = executable;
        if (exec != null) try {
            setClassLoader();
            final class ExecuteContextImpl implements ExecuteContext<T>, TaskFactory {
                @Override
                public void lockAsynchronously(final TransactionalLock lock, final LockListener listener) {
                    if (lock == null) {
                        throw TXN.methodParameterIsNull("lock");
                    }
                    if (listener == null) {
                        throw TXN.methodParameterIsNull("listener");
                    }
                    lock.lockAsynchronously(getTransaction(), listener);
                }

                @Override
                public boolean tryLock(final TransactionalLock lock) {
                    if (lock == null) {
                        throw TXN.methodParameterIsNull("lock");
                    }
                    return lock.tryLock(getTransaction());
                }

                @Override
                public void complete(final T result) {
                    execComplete(result);
                }

                @Override
                public void complete() {
                    complete(null);
                }

                @Override
                public boolean isCancelRequested() {
                    synchronized (TaskControllerImpl.this) {
                        return Bits.allAreSet(state, FLAG_ROLLBACK_REQ | FLAG_CANCEL_REQ);
                    }
                }

                @Override
                public void cancelled() {
                    execCancelled();
                }

                @Override
                public void addProblem(final Problem reason) {
                    problemReport.addProblem(reason);
                }

                @Override
                public void addProblem(final Problem.Severity severity, final String message) {
                    addProblem(new Problem(TaskControllerImpl.this, message, severity));
                }

                @Override
                public void addProblem(final Problem.Severity severity, final String message, final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, message, cause, severity));
                }

                @Override
                public void addProblem(final String message, final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, message, cause));
                }

                @Override
                public void addProblem(final String message) {
                    addProblem(new Problem(TaskControllerImpl.this, message));
                }

                @Override
                public void addProblem(final Throwable cause) {
                    addProblem(new Problem(TaskControllerImpl.this, cause));
                }

                @Override
                public <N> TaskBuilder<N> newTask(final Executable<N> task) throws IllegalStateException {
                    return new TaskBuilderImpl<N>(getTransaction(), TaskControllerImpl.this, task);
                }

                @Override
                public TaskBuilder<Void> newTask() throws IllegalStateException {
                    return new TaskBuilderImpl<Void>(getTransaction(), TaskControllerImpl.this);
                }
            }
            exec.execute(new ExecuteContextImpl());
        } catch (Throwable t) {
            MSCLogger.TASK.taskExecutionFailed(t, exec);
            problemReport.addProblem(new Problem(this, t, Problem.Severity.CRITICAL));
        } finally {
            unsetClassLoader();
        }
    }

    @Override
    public void childExecuted(final boolean userThread) {
        assert ! holdsLock(this);
        int state = 0;
        TaskParent adopter = null;
        synchronized (this) {
            if (this.adopter != null) {
                adopter = this.adopter;
            } else {
                unfinishedChildren--;
                state = this.state;
                if (userThread) state |= FLAG_USER_THREAD;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            }
        }
        if (adopter == null) {
            executeTasks(state);
        } else {
            adopter.childExecuted(userThread);
        }
    }

    @Override
    public void childValidated(final boolean userThread) {
        assert ! holdsLock(this);
        int state = 0;
        TaskParent adopter = null;
        synchronized (this) {
            if (this.adopter != null) {
                adopter = this.adopter;
            } else {
                unvalidatedChildren--;
                state = this.state;
                if (userThread) state |= FLAG_USER_THREAD;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            }
        }
        if (adopter == null) {
            executeTasks(state);
        } else {
            adopter.childValidated(userThread);
        }
    }

    @Override
    public void childTerminated(final boolean userThread) {
        assert ! holdsLock(this);
        int state = 0;
        TaskParent adopter = null;
        synchronized (this) {
            if (this.adopter != null) {
                adopter = this.adopter;
            } else {
                unterminatedChildren--;
                state = this.state;
                if (userThread) state |= FLAG_USER_THREAD;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            }
        }
        if (adopter == null) {
            executeTasks(state);
        } else {
            adopter.childTerminated(userThread);
        }
    }

    @Override
    public void childAdded(final TaskChild child, final boolean userThread) throws InvalidTransactionStateException {
        assert ! holdsLock(this);
        int state = 0;
        TaskParent adopter = null;
        synchronized (this) {
            if (this.adopter != null) {
                adopter = this.adopter;
            } else {
                state = this.state;
                if (stateIsIn(state, STATE_EXECUTE)) {
                    unfinishedChildren++;
                    unvalidatedChildren++;
                    unterminatedChildren++;
                    children.add((TaskControllerImpl<?>) child);
                    if (userThread)
                        state |= FLAG_USER_THREAD;
                    state = transition(state);
                    this.state = state & PERSISTENT_STATE;
                } else {
                    if (userThread) {
                        throw new IllegalStateException("Dependent may not be added at this point");
                    } else {
                        // todo log and ignore...
                        return;
                    }
                }
            }
        }
        if (adopter == null) {
            executeTasks(state);
        } else {
            adopter.childAdded(child, userThread);
        }
    }

    public void dependencyExecutionComplete(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unfinishedDependencies--;
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    public void childInitiateRollback(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            state |= FLAG_ROLLBACK_REQ;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    public void childInitiateValidate(final boolean userThread) {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            state = this.state | FLAG_VALIDATE_REQ;
            if (userThread) state |= FLAG_USER_THREAD;
            state = transition(state);
            this.state = state & PERSISTENT_STATE;
        }
        executeTasks(state);
    }

    public void childInitiateCommit(final boolean userThread) {
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
        boolean dependencyCancelled = false;

        synchronized (this) {
            state = this.state;
            if (userThread) state |= FLAG_USER_THREAD;
            if (stateIsIn(state, STATE_EXECUTE_WAIT, STATE_EXECUTE, STATE_EXECUTE_CHILDREN_WAIT, STATE_EXECUTE_DONE, STATE_TERMINATE_WAIT, STATE_TERMINATED)) {
                dependents.add(dependent);
                unterminatedDependents++;
                state = transition(state);
                this.state = state & PERSISTENT_STATE;
            } else {
                if (userThread) {
                    throw new IllegalStateException("Dependent may not be added at this point");
                } else {
                    // todo log and ignore...
                    return;
                }
            }
            switch (stateOf(state)) {
                case STATE_TERMINATED:
                case STATE_TERMINATE_WAIT: {
                    if (Bits.anyAreSet(state, FLAG_CANCEL_REQ | FLAG_ROLLBACK_REQ)) {
                        dependencyCancelled = true;
                        break;
                    }
                }
                case STATE_EXECUTE_DONE: dependencyDone = true;
            }
        }
        if (dependencyDone) {
            dependent.dependencyExecutionComplete(userThread);
        } else if (dependencyCancelled) {
            dependent.forceCancel(userThread);
        }
        executeTasks(state);
    }

    void install() {
        assert ! holdsLock(this);
        int state;
        synchronized (this) {
            unfinishedDependencies = dependencies.length;
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
        TaskControllerImpl<?> dependency;
        for (int i = 0; i < dependencies.length; i++) {
            dependency = dependencies[i];
            try {
                dependency.dependentAdded(this, true);
            } catch (IllegalStateException e) {
                for (; i >= 0; i --) {
                    dependency = dependencies[i];
                    dependency.dependentTerminated(true);
                }
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
            return TaskControllerImpl.this.toString() + ".AsyncTask@" + System.identityHashCode(this);
        }
    }
}
