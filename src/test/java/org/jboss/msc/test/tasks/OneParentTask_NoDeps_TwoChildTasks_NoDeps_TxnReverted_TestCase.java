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
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.test.utils.TestExecutable;
import org.jboss.msc.test.utils.TestRevertible;
import org.jboss.msc.test.utils.TestValidatable;
import org.jboss.msc.txn.AbortResult;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.CompletionListener;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.PrepareResult;
import org.jboss.msc.txn.RollbackResult;
import org.jboss.msc.txn.TaskController;
import org.junit.Test;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class OneParentTask_NoDeps_TwoChildTasks_NoDeps_TxnReverted_TestCase extends AbstractTransactionTest {

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
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch childValidateSignal = new CountDownLatch(1);
        final CountDownLatch childRollbackSignal = new CountDownLatch(1);
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>();
        final TestValidatable child0v = new TestValidatable(childValidateSignal);
        final TestRevertible child0r = new TestRevertible(childRollbackSignal);
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>();
        final TestValidatable child1v = new TestValidatable(childValidateSignal);
        final TestRevertible child1r = new TestRevertible(childRollbackSignal);
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0v, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1v, child1r);
                assertNotNull(child1Controller);
            }
        };
        final TestValidatable parent0v = new TestValidatable();
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0v, parent0r);
        assertNotNull(parentController);
        // preparing transaction - children validation is blocked
        final CompletionListener<PrepareResult<BasicTransaction>> prepareListener = new CompletionListener<>();
        prepare(transaction, prepareListener);
        try {
            prepareListener.awaitCompletionUninterruptibly(100, TimeUnit.MILLISECONDS);
            fail("Timeout expected");
        } catch (TimeoutException ignored) {}
        // let children to finish validate
        childValidateSignal.countDown();
        prepareListener.awaitCompletionUninterruptibly();
        assertPrepared(transaction);
        // transaction prepared
        assertCalled(parent0e);
        assertCalled(child0e);
        assertCalled(child1e);
        assertCalled(parent0v);
        assertCalled(child0v);
        assertCalled(child1v);
        assertNotCalled(parent0r);
        assertNotCalled(child0r);
        assertNotCalled(child1r);
        assertCallOrder(parent0e, child0e, parent0v, child0v);
        assertCallOrder(parent0e, child1e, parent0v, child1v);
        // reverting transaction - children rollback is blocked
        assertTrue(canCommit(transaction));
        final CompletionListener<AbortResult<BasicTransaction>> abortListener = new CompletionListener<>();
        abort(transaction, abortListener);
        try {
            abortListener.awaitCompletionUninterruptibly(100, TimeUnit.MILLISECONDS);
            fail("Timeout expected");
        } catch (TimeoutException ignored) {}
        // let children to finish commit
        childRollbackSignal.countDown();
        abortListener.awaitCompletionUninterruptibly();
        assertAborted(transaction);
        // transaction committed
        assertCalled(parent0e);
        assertCalled(child0e);
        assertCalled(child1e);
        assertCalled(parent0v);
        assertCalled(child0v);
        assertCalled(child1v);
        assertCalled(parent0r);
        assertCalled(child0r);
        assertCalled(child1r);
        assertCallOrder(parent0e, child0e, parent0v, child0v, child0r, parent0r);
        assertCallOrder(parent0e, child1e, parent0v, child1v, child1r, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task completes at EXECUTE
     * <LI>child0 cancels at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>(true, signal);
        final TestValidatable child0v = new TestValidatable();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true, signal);
        final TestValidatable child1v = new TestValidatable();
        final TestRevertible child1r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(signal) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0v, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1v, child1r);
                assertNotNull(child1Controller);
            }
        };
        final TestValidatable parent0v = new TestValidatable();
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0v, parent0r);
        assertNotNull(parentController);
        // reverting transaction
        final CompletionListener<RollbackResult<BasicTransaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0v);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0v);
        assertNotCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1v);
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
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase3() {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>(signal);
        final TestValidatable child0v = new TestValidatable();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true, signal);
        final TestValidatable child1v = new TestValidatable();
        final TestRevertible child1r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(signal) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0v, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1v, child1r);
                assertNotNull(child1Controller);
            }
        };
        final TestValidatable parent0v = new TestValidatable();
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0v, parent0r);
        assertNotNull(parentController);
        // reverting transaction
        final CompletionListener<RollbackResult<BasicTransaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0v);
        assertCalled(parent0r);
        // assert child0 calls
        assertCalled(child0e);
        assertNotCalled(child0v);
        assertCalled(child0r);
        // assert child1 calls
        assertCalled(child1e);
        assertNotCalled(child1v);
        assertNotCalled(child1r);
        assertCallOrder(parent0e, child0e, child0r, parent0r);
        assertCallOrder(parent0e, child1e, parent0r);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>parent task cancels at EXECUTE
     * <LI>child0 completes at EXECUTE</LI>
     * <LI>child1 cancels at EXECUTE</LI>
     * <LI>no dependencies</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // preparing child0 task
        final TestExecutable<Void> child0e = new TestExecutable<Void>(signal);
        final TestValidatable child0v = new TestValidatable();
        final TestRevertible child0r = new TestRevertible();
        // preparing child1 task
        final TestExecutable<Void> child1e = new TestExecutable<Void>(true, signal);
        final TestValidatable child1v = new TestValidatable();
        final TestRevertible child1r = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> parent0e = new TestExecutable<Void>(true, signal) {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child0 task
                final TaskController<Void> child0Controller = newTask(ctx, child0e, child0v, child0r);
                assertNotNull(child0Controller);
                // installing child1 task
                final TaskController<Void> child1Controller = newTask(ctx, child1e, child1v, child1r);
                assertNotNull(child1Controller);
            }
        };
        final TestValidatable parent0v = new TestValidatable();
        final TestRevertible parent0r = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, parent0e, parent0v, parent0r);
        assertNotNull(parentController);
        // reverting transaction
        final CompletionListener<RollbackResult<BasicTransaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        // assert parent0 calls
        assertCalled(parent0e);
        assertNotCalled(parent0v);
        assertNotCalled(parent0r);
        // assert child0 calls
        // child0e.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(child0v);
        // child0r.wasCalled() can return either true of false, depends on threads scheduling
        // assert child1 calls
        // child1e.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(child1v);
        assertNotCalled(child1r);
        if (child0e.wasCalled()) {
            assertCalled(child0r);
            assertCallOrder(parent0e, child0e, child0r);
        }
        if (child1e.wasCalled()) {
            assertCallOrder(parent0e, child1e);
        }
    }
}
