/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.AbstractTransactionTest;
import org.jboss.msc.txn.TransactionHoldHandle;
import org.jboss.msc.txn.UpdateTransaction;
import org.jboss.msc.util.CompletionListener;
import org.jboss.msc.util.Listener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class TransactionHoldHandleTestCase extends AbstractTransactionTest {

    @Test
    public void blockTxnHoldHandleUntillAllServicesAreUp() throws Exception {
        final StringBuffer testLog = new StringBuffer();
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.createServiceContainer();
        final ServiceRegistry registry = container.newRegistry();
        final TransactionHoldHandle handle = updateTxn.acquireHoldHandle();
        final PrepareCompletionListener<UpdateTransaction> prepareCallback = new PrepareCompletionListener<>(testLog, updateTxn);
        txnController.prepare(updateTxn, prepareCallback);
        // assert transaction is active
        assertFalse(updateTxn.isPrepared());
        assertFalse(prepareCallback.isCompleted());
        // install first service
        final TestService<Void> service1 = new TestService<>("1", testLog);
        final ServiceName service1Name = ServiceName.of("test1");
        final ServiceBuilder<Void> sb1 = txnController.getServiceContext(updateTxn).addService(registry, service1Name);
        sb1.setService(service1).setMode(ServiceMode.ACTIVE).install();
        // assert transaction is active
        assertFalse(updateTxn.isPrepared());
        assertFalse(prepareCallback.isCompleted());
        // install second service
        final TestService<Void> service2 = new TestService<>("2", testLog);
        final ServiceName service2Name = ServiceName.of("test2");
        final ServiceBuilder<Void> sb2 = txnController.getServiceContext(updateTxn).addService(registry, service2Name);
        sb2.setService(service2).addDependency(service1Name);
        sb2.setMode(ServiceMode.ACTIVE).install();
        // wait for all services to start
        service1.awaitStart();
        service2.awaitStart();
        // assert transaction is still active
        assertFalse(updateTxn.isPrepared());
        assertFalse(prepareCallback.isCompleted());
        // release txn hold handle
        handle.release();
        // synchronize with prepare phase completion
        prepareCallback.awaitCompletion();
        // assert transaction is prepared
        assertTrue(updateTxn.isPrepared());
        assertTrue(prepareCallback.isCompleted());
        // commit transaction
        commit(updateTxn);
        // assert expected output
        assertEquals(testLog.toString(), "[TestService[1] STARTED][TestService[2] STARTED][PrepareCompletionListener PASSED]");
    }

    private static final class TestService<T> implements Service<T> {

        private final String id;
        private final StringBuffer log;
        private final CountDownLatch startLatch = new CountDownLatch(1);

        private TestService(final String id, final StringBuffer log) {
            this.id = id;
            this.log = log;
        }

        @Override
        public void start(final StartContext<T> startContext) {
            log.append("[TestService[" + id + "] STARTED]");
            startContext.complete();
            startLatch.countDown();
        }

        @Override
        public void stop(final StopContext stopContext) {
            log.append("[TestService[" + id + "] STOPPED]");
            stopContext.complete();
        }

        public void awaitStart() throws Exception {
            startLatch.await();
        }

    }

    private static final class PrepareCompletionListener<UpdateTransaction> implements Listener<UpdateTransaction> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final StringBuffer sb;
        private final UpdateTransaction txn;
        private volatile UpdateTransaction result;

        private PrepareCompletionListener(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        public void handleEvent(final UpdateTransaction result) {
            this.result = result;
            if (txn == result) {
                sb.append("[PrepareCompletionListener PASSED]");
            } else {
                sb.append("[PrepareCompletionListener FAILED]");
            }
            latch.countDown();
        }

        public UpdateTransaction awaitCompletion() throws InterruptedException {
            if (result != null) return result;
            boolean intr = false;
            try {
                while (true) {
                    try {
                        latch.await();
                        break;
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                return result;
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }

        public boolean isCompleted() {
            return result != null;
        }

    }

}
