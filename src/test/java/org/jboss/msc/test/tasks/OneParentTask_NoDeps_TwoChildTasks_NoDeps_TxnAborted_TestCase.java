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

import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.test.utils.TestExecutable;
import org.jboss.msc.test.utils.TestRevertible;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class OneParentTask_NoDeps_TwoChildTasks_NoDeps_TxnAborted_TestCase extends AbstractTransactionTest {

    /**
     * Scenario:
     * <UL>
     * <LI>parent task completes at EXECUTE
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
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
        assertCalled(child1r);
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task completes at EXECUTE
     * <LI>child0 cancels at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>(true);
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
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
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        assertCallOrder(parent0e, child0e, parent0r);
        assertCallOrder(parent0e, child1e, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task completes at EXECUTE
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase3() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
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
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task cancels at EXECUTE
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 completes at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestRevertible child1r = new TestRevertible();
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
        assertCalled(child1r);
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e, child1r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task cancels at EXECUTE
     * <LI>child0 cancels at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase5() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>(true);
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
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
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1r);
        assertCallOrder(parent0e, child0e);
        assertCallOrder(parent0e, child1e);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task cancels at EXECUTE
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction prepared</LI>
     * <LI>transaction aborted</LI>
     * </UL>
     */
    @Test
    public void usecase6() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true);
        final TestRevertible child1r = new TestRevertible();
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
        assertCallOrder(parent0e, child0e, child0r);
        assertCallOrder(parent0e, child1e);
    }

}
