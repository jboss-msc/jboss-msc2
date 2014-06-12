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
import org.jboss.msc.txn.CompletionListener;
import org.jboss.msc.txn.RollbackResult;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.Transaction;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ThreeParentTasks_WithDeps_NoChildTasks_NoDeps_TxnReverted_TestCase extends AbstractTransactionTest {

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        // e1.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        // e1.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase3() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
        }
        if (e2.wasCalled()) {
            assertNotCalled(r2);
        }
        if (e1.wasCalled() && e2.wasCalled()) {
            assertCallOrder(e1, e2, r1);
        }
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
        }
        if (e2.wasCalled()) {
            assertCalled(r2);
            assertCallOrder(e1, e2, r2, r1);
        }
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase5() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        // e1.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase6() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        // e1.wasCalled() can return either true or false, depends on threads scheduling
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase7() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
        }
        if (e0.wasCalled() && e2.wasCalled()) {
            assertNotCalled(r2);
            assertCallOrder(e0, e2, r0);
            assertCallOrder(e1, e2, r1);
        }
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase8() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
        }
        if (e0.wasCalled() && e2.wasCalled()) {
            assertCalled(r2);
            assertCallOrder(e2, r2);
            assertCallOrder(e0, e2, r2, r0);
            assertCallOrder(e1, e2, r2, r1);
        }
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE, depends on task0</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase9() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCallOrder(e0, e1, r0);
        }
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase10() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCallOrder(e0, e1, r0);
        }
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase11() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
            assertCallOrder(e0, e1, r1, r0);
        }
        if (e2.wasCalled()) {
            assertCallOrder(e0, e1, e2, r1, r0);
        }
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase12() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
            assertCallOrder(e0, e1, r1, r0);
        }
        if (e2.wasCalled()) {
            assertCalled(r2);
            assertCallOrder(e0, e1, e2, r2, r1, r0);
        }
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE, depends on task0</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase13() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCallOrder(e0, e1, r0);
        }
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 cancels at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase14() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(true, signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCallOrder(e0, e1, r0);
        }
        assertNotCalled(r1);
        assertNotCalled(e2);
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 cancels at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase15() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(true, signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
            assertCallOrder(e0, e1, r1, r0);
        }
        if (e2.wasCalled()) {
            assertCallOrder(e0, e1, e2, r1, r0);
        }
        assertNotCalled(r2);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction rolled back</LI>
     * </UL>
     */
    @Test
    public void usecase16() {
        final UpdateTransaction transaction = newUpdateTransaction();
        final CountDownLatch signal = new CountDownLatch(1);
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<Void>(signal);
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> task0Controller = newTask(transaction, e0, r0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<Void>(signal);
        final TestRevertible r1 = new TestRevertible();
        final TaskController<Void> task1Controller = newTask(transaction, e1, r1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<Void>(signal);
        final TestRevertible r2 = new TestRevertible();
        final TaskController<Void> task2Controller = newTask(transaction, e2, r2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // reverting transaction
        final CompletionListener<RollbackResult<? extends Transaction>> rollbackListener = new CompletionListener<>();
        rollback(transaction, rollbackListener);
        signal.countDown();
        rollbackListener.awaitCompletionUninterruptibly();
        if (e0.wasCalled()) {
            assertCalled(r0);
            assertCallOrder(e0, r0);
        }
        if (e1.wasCalled()) {
            assertCalled(r1);
            assertCallOrder(e1, r1);
            assertCallOrder(e0, e1, r1, r0);
        }
        if (e2.wasCalled()) {
            assertCalled(r2);
            assertCallOrder(e0, e1, e2, r2, r1, r0);
        }
    }
}
