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

import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;

import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.CommitListener;
import org.jboss.msc.txn.CommitResult;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.PrepareListener;
import org.jboss.msc.txn.PrepareResult;
import org.jboss.msc.txn.RollbackListener;
import org.jboss.msc.txn.RollbackResult;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ListenersTestCase extends AbstractTransactionTest {

    private volatile boolean prepareCalled;
    private volatile boolean commitCalled;
    private volatile boolean rollbackCalled;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareCalled = commitCalled = rollbackCalled = false;
    }

    /**
     * Usecase 1 (ACTIVE -> PREPARING -> ROLLBACK -> ROLLED_BACK)
     *
     * If rollback() have been called on transaction
     * while prepare() was in progress it might result
     * in prepare listener not to be notified when
     * transaction have been rolled back.
     */
    @Test
    public void testPrepareRollbackListeners() throws InterruptedException {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch rollbackSignal = new CountDownLatch(1);
        final CountDownLatch executeSignal = new CountDownLatch(1);
        final Executable<Object> executable = new Executable<Object>() {
            @Override public void execute(final ExecuteContext<Object> context) {
                rollbackSignal.countDown();
                try { executeSignal.await(); } catch (final InterruptedException ignore) {}
                context.complete();
            }
        };
        txnController.newTask(transaction, executable).release();
        txnController.prepare(transaction, new PrepareListener<BasicTransaction>() {
            @Override public void handleEvent(final PrepareResult<BasicTransaction> result) {
                prepareCalled = true;
            }
        });
        rollbackSignal.await();
        final CountDownLatch finishSignal = new CountDownLatch(1);
        txnController.rollback(transaction, new RollbackListener<BasicTransaction>() {
            @Override public void handleEvent(final RollbackResult<BasicTransaction> result) {
                rollbackCalled = true;
                finishSignal.countDown();
            }
        });
        executeSignal.countDown();
        finishSignal.await();
        assertTrue(prepareCalled);
        assertTrue(rollbackCalled);
    }

    /**
     * Usecase 2 (ACTIVE -> PREPARING -> PREPARED -> ROLLBACK -> ROLLED_BACK)
     *
     * If both commit() & rollback() (in this exact order) have been called
     * on transaction while prepare() was in progress
     * it always resulted in commit listener to be lost
     * and not to be notified about transaction termination.
     */
    @Test
    public void testPrepareCommitRollbackListeners() throws InterruptedException {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch rollbackSignal = new CountDownLatch(1);
        final CountDownLatch executeSignal = new CountDownLatch(1);
        final Executable<Object> executable = new Executable<Object>() {
            @Override public void execute(final ExecuteContext<Object> context) {
                rollbackSignal.countDown();
                try { executeSignal.await(); } catch (final InterruptedException ignore) {}
                context.complete();
            }
        };
        txnController.newTask(transaction, executable).release();
        txnController.prepare(transaction, new PrepareListener<BasicTransaction>() {
            @Override public void handleEvent(final PrepareResult<BasicTransaction> result) {
                prepareCalled = true;
            }
        });
        rollbackSignal.await();
        txnController.commit(transaction, new CommitListener<BasicTransaction>() {
            @Override public void handleEvent(final CommitResult<BasicTransaction> result) {
                commitCalled = true;
            }
        });
        final CountDownLatch finishSignal = new CountDownLatch(1);
        txnController.rollback(transaction, new RollbackListener<BasicTransaction>() {
            @Override public void handleEvent(final RollbackResult<BasicTransaction> result) {
                rollbackCalled = true;
                finishSignal.countDown();
            }
        });
        executeSignal.countDown();
        finishSignal.await();
        assertTrue(prepareCalled);
        assertTrue(commitCalled);
        assertTrue(rollbackCalled);
    }

    /**
     * Usecase 3 (ACTIVE -> PREPARING -> PREPARED -> ROLLBACK -> ROLLED_BACK)
     *
     * If both rollback() & commit() (in this exact order) have been called
     * on transaction while prepare() was in progress
     * it always resulted in rollback listener to be lost
     * and not to be notified about transaction termination.
     */
    @Test
    public void testPrepareRollbackCommitListeners() throws InterruptedException {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch rollbackSignal = new CountDownLatch(1);
        final CountDownLatch executeSignal = new CountDownLatch(1);
        final Executable<Object> executable = new Executable<Object>() {
            @Override public void execute(final ExecuteContext<Object> context) {
                rollbackSignal.countDown();
                try { executeSignal.await(); } catch (final InterruptedException ignored) {}
                context.complete();
            }
        };
        txnController.newTask(transaction, executable).release();
        txnController.prepare(transaction, new PrepareListener<BasicTransaction>() {
            @Override public void handleEvent(final PrepareResult<BasicTransaction> result) {
                prepareCalled = true;
            }
        });
        rollbackSignal.await();
        final CountDownLatch finishSignal = new CountDownLatch(1);
        txnController.rollback(transaction, new RollbackListener<BasicTransaction>() {
            @Override public void handleEvent(final RollbackResult<BasicTransaction> result) {
                rollbackCalled = true;
                finishSignal.countDown();
            }
        });
        txnController.commit(transaction, new CommitListener<BasicTransaction>() {
            @Override public void handleEvent(final CommitResult<BasicTransaction> result) {
                commitCalled = true;
            }
        });
        executeSignal.countDown();
        finishSignal.await();
        assertTrue(prepareCalled);
        assertTrue(commitCalled);
        assertTrue(rollbackCalled);
    }
}