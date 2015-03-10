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
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.AbstractTransactionTest;
import org.jboss.msc.txn.Action;
import org.jboss.msc.txn.ActionContext;
import org.jboss.msc.txn.UpdateTransaction;
import org.jboss.msc.util.CompletionListener;
import org.jboss.msc.util.Listener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class TransactionPostPhaseListenersTestCase extends AbstractTransactionTest {

    @Test
    public void restartTransaction() throws Exception {
        final StringBuffer testLog = new StringBuffer();
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.createServiceContainer();
        final ServiceRegistry registry = container.newRegistry();
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder sb = txnController.getServiceContext(updateTxn).addService(registry, serviceName);
        final TestService service = new TestService(testLog);
        final ServiceController serviceController = sb.setService(service).setMode(ServiceMode.ACTIVE).install();
        final PostPrepareAction<UpdateTransaction> postPrepareAction1 = new PostPrepareAction<>(testLog, updateTxn);
        updateTxn.addPostPrepare(postPrepareAction1);
        final PostRestartAction<UpdateTransaction> postRestartAction = new PostRestartAction<>(testLog, updateTxn);
        updateTxn.addPostRestart(postRestartAction);
        final PrepareCompletionListener<UpdateTransaction> prepareListener1 = new PrepareCompletionListener<>(testLog, updateTxn);
        txnController.prepare(updateTxn, prepareListener1);
        prepareListener1.awaitCompletion();
        final RestartCompletionListener<UpdateTransaction> restartListener = new RestartCompletionListener<>(testLog, updateTxn);
        txnController.restart(updateTxn, restartListener);
        updateTxn = restartListener.awaitCompletion();
        serviceController.remove(updateTxn);
        final PostPrepareAction<UpdateTransaction> postPrepareAction2 = new PostPrepareAction<>(testLog, updateTxn);
        updateTxn.addPostPrepare(postPrepareAction2);
        final PostCommitAction<UpdateTransaction> postCommitAction = new PostCommitAction<>(testLog, updateTxn);
        updateTxn.addPostCommit(postCommitAction);
        final PrepareCompletionListener<UpdateTransaction> prepareListener2 = new PrepareCompletionListener<>(testLog, updateTxn);
        txnController.prepare(updateTxn, prepareListener2);
        prepareListener2.awaitCompletion();
        final CommitCompletionListener<UpdateTransaction> commitListener = new CommitCompletionListener<>(testLog, updateTxn);
        txnController.commit(updateTxn, commitListener);
        commitListener.awaitCompletion();
        assertEquals(testLog.toString(), "[TestService STARTED][PostPrepareAction PASSED][PrepareCompletionListener PASSED][PostRestartAction PASSED][RestartCompletionListener PASSED][TestService STOPPED][PostPrepareAction PASSED][PrepareCompletionListener PASSED][PostCommitAction PASSED][CommitCompletionListener PASSED]");
    }

    private static final class TestService implements Service<Void> {

        private final StringBuffer sb;

        private TestService(final StringBuffer sb) {
            this.sb = sb;
        }

        @Override
        public void start(final StartContext<Void> startContext) {
            sb.append("[TestService STARTED]");
            startContext.complete();
        }

        @Override
        public void stop(final StopContext stopContext) {
            sb.append("[TestService STOPPED]");
            stopContext.complete();
        }

    }

    private static final class PostPrepareAction<T extends UpdateTransaction> implements Action<T> {

        private final StringBuffer sb;
        private final UpdateTransaction txn;

        private PostPrepareAction(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        @Override
        public void handleEvent(final ActionContext<T> ctx) {
            if (ctx.getTransaction() == txn) {
                sb.append("[PostPrepareAction PASSED]");
            } else {
                sb.append("[PostPrepareAction FAILED]");
            }
            ctx.complete();
        }
    }

    private static final class PostRestartAction<T extends UpdateTransaction> implements Action<T> {

        private final StringBuffer sb;
        private final UpdateTransaction txn;

        private PostRestartAction(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        @Override
        public void handleEvent(final ActionContext<T> ctx) {
            if (ctx.getTransaction() == txn) {
                sb.append("[PostRestartAction PASSED]");
            } else {
                sb.append("[PostRestartAction FAILED]");
            }
            ctx.complete();
        }
    }

    private static final class PostCommitAction<T extends UpdateTransaction> implements Action<T> {

        private final StringBuffer sb;
        private final UpdateTransaction txn;

        private PostCommitAction(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        @Override
        public void handleEvent(final ActionContext<T> ctx) {
            if (ctx.getTransaction() == txn) {
                sb.append("[PostCommitAction PASSED]");
            } else {
                sb.append("[PostCommitAction FAILED]");
            }
            ctx.complete();
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

    }

    private static final class RestartCompletionListener<UpdateTransaction> implements Listener<UpdateTransaction> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final StringBuffer sb;
        private final UpdateTransaction txn;
        private volatile UpdateTransaction result;

        private RestartCompletionListener(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        public void handleEvent(final UpdateTransaction result) {
            this.result = result;
            if (txn != result) {
                sb.append("[RestartCompletionListener PASSED]");
            } else {
                sb.append("[RestartCompletionListener FAILED]");
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

    }

    private static final class CommitCompletionListener<UpdateTransaction> implements Listener<UpdateTransaction> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final StringBuffer sb;
        private final UpdateTransaction txn;
        private volatile UpdateTransaction result;

        private CommitCompletionListener(final StringBuffer sb, final UpdateTransaction txn) {
            this.sb = sb;
            this.txn = txn;
        }

        public void handleEvent(final UpdateTransaction result) {
            this.result = result;
            if (txn == result) {
                sb.append("[CommitCompletionListener PASSED]");
            } else {
                sb.append("[CommitCompletionListener FAILED]");
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

    }

}
