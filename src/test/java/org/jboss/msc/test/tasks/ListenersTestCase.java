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
import org.jboss.msc.txn.AbortResult;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.Listener;
import org.jboss.msc.txn.PrepareResult;
import org.jboss.msc.txn.Validatable;
import org.jboss.msc.txn.ValidateContext;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ListenersTestCase extends AbstractTransactionTest {

    private volatile boolean prepareCalled;
    private volatile boolean abortCalled;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareCalled = abortCalled = false;
    }

    /**
     * Usecase 1 (ACTIVE -> PREPARING -> ROLLBACK -> ROLLED_BACK)
     *
     * If abort() have been called on transaction
     * while prepare() was in progress it might result
     * in prepare listener not to be notified when
     * transaction have been aborted.
     */
    @Test
    public void testPrepareAbortListeners() throws InterruptedException {
        final BasicTransaction transaction = newTransaction();
        final CountDownLatch abortSignal = new CountDownLatch(1);
        final CountDownLatch executeSignal = new CountDownLatch(1);
        final Executable<Object> executable = new Executable<Object>() {
            @Override public void execute(final ExecuteContext<Object> context) {
                abortSignal.countDown();
                context.complete();
            }
        };
        final Validatable validatable = new Validatable() {
            @Override
            public void validate(final ValidateContext context) {
                try { executeSignal.await(); } catch (final InterruptedException ignored) {}
            }
        };
        txnController.newTask(transaction, executable).setValidatable(validatable).release();
        txnController.prepare(transaction, new Listener<PrepareResult<BasicTransaction>>() {
            @Override public void handleEvent(final PrepareResult<BasicTransaction> result) {
                prepareCalled = true;
            }
        });
        abortSignal.await();
        final CountDownLatch finishSignal = new CountDownLatch(1);
        txnController.abort(transaction, new Listener<AbortResult<BasicTransaction>>() {
            @Override public void handleEvent(final AbortResult<BasicTransaction> result) {
                abortCalled = true;
                finishSignal.countDown();
            }
        });
        executeSignal.countDown();
        finishSignal.await();
        assertTrue(prepareCalled);
        assertTrue(abortCalled);
    }

}
