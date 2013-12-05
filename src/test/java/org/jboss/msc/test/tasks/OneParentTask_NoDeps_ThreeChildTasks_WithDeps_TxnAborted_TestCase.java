/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
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

package org.jboss.msc.test.tasks;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.test.utils.TestExecutable;
import org.jboss.msc.test.utils.TestRevertible;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.TaskController;
import org.junit.Test;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class OneParentTask_NoDeps_ThreeChildTasks_WithDeps_TxnAborted_TestCase extends AbstractTransactionTest {

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase3() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child2e, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child2e, child2r, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase5() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase6() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase7() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child2e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child2e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child2e, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase8() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child2e, child2r, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase9() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase10() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase11() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child1r, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase12() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child2r, child1r, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase13() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase14() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase15() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child1r, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent completes at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase16() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child2r, child1r, child0r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase17() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase18() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase19() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child2e, child1r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase20() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child2e, child2r, child1r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase21() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase22() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase23() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child2e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child2e, child0r);
        assertCallOrder(parent0e, child1e, child2e, child1r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase24() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child2e, child2r, child1r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase25() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase26() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase27() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child1r, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase28() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child2r, child1r, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase29() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase30() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertNotCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 cancels at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase31() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>(true);
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child1r, child0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent cancels at EXECUTE</LI>
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE, depends on child0</LI>
     * <LI>child2 completes at EXECUTE, depends on child1 and child0</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase32() {
        final BasicTransaction transaction = newTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
        // preparing child2 task
        final TestExecutable<Void> child2e = new TestExecutable<Void>();
        final TestRevertible child2r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1r, child0Controller);
                assertNotNull(child1Controller);
                // installing child2 task
                final TaskController<Void> child2Controller = newTask(ctx, child2e, child2r, child0Controller, child1Controller);
                assertNotNull(child2Controller);
            }
        };
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0r);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertNotCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e);
        // aborting transaction
        assertTrue(canCommit(transaction));
        abort(transaction);
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertCalled(child1r);
        // assert child2 calls
        assertCalled(child2e);
        assertCalled(child2r);
        // assert tasks ordering
        assertCallOrder(parent0e, child0e, child1e, child2e, child2r, child1r, child0r);
    }

}
