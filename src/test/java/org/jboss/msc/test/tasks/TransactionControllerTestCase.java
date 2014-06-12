/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.test.utils.TestService;
import org.jboss.msc.txn.CompletionListener;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.ReadTransaction;
import org.jboss.msc.txn.TransactionController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class TransactionControllerTestCase extends AbstractTransactionTest {

    @Test
    public void upgradeTransaction() throws Exception {
        final CompletionListener<ReadTransaction> createListener = new CompletionListener<>();
        txnController.createReadTransaction(defaultExecutor, createListener);
        final ReadTransaction readTxn = createListener.awaitCompletion();
        assertNotNull(readTxn);
        final CompletionListener<UpdateTransaction> upgradeListener = new CompletionListener<>();
        boolean upgraded = txnController.upgradeTransaction(readTxn, upgradeListener);
        assertTrue(upgraded);
        final UpdateTransaction updateTxn = upgradeListener.awaitCompletion();
        assertNotNull(updateTxn);
        assertTrue(updateTxn != readTxn);
        upgraded = txnController.upgradeTransaction(updateTxn, new CompletionListener<UpdateTransaction>());
        assertFalse(upgraded); // already upgraded transaction cannot be upgraded again
        prepare(readTxn);
        commit(updateTxn); // users can call transaction lifecycle methods on both Read and Update transaction interchangeably (in upgrade case)
        assertTrue(readTxn.isTerminated());
        assertTrue(updateTxn.isTerminated());
    }

    @Test
    public void upgradeTransactionFailed() throws Exception {
        final CompletionListener<ReadTransaction> readTxnCreateListener = new CompletionListener<>();
        txnController.createReadTransaction(defaultExecutor, readTxnCreateListener);
        final ReadTransaction readTxn = readTxnCreateListener.awaitCompletion();
        assertNotNull(readTxn);
        final CompletionListener<UpdateTransaction> updateTxnCreateListener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, updateTxnCreateListener);
        final boolean upgraded = txnController.upgradeTransaction(readTxn, new CompletionListener<UpdateTransaction>());
        assertFalse(upgraded); // upgrade of read transaction will fail if there's pending update transaction in request queue
        prepare(readTxn);
        commit(readTxn);
        final UpdateTransaction updateTxn = updateTxnCreateListener.awaitCompletion();
        assertNotNull(updateTxn);
        prepare(updateTxn);
        commit(updateTxn);
        assertTrue(readTxn.isTerminated());
        assertTrue(updateTxn.isTerminated());
    }

    @Test
    public void downgradeTransaction() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, createListener);
        final UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final CompletionListener<ReadTransaction> downgradeListener = new CompletionListener<>();
        boolean downgraded = txnController.downgradeTransaction(updateTxn, downgradeListener);
        assertTrue(downgraded);
        final ReadTransaction readTxn = downgradeListener.awaitCompletion();
        assertNotNull(readTxn);
        assertTrue(updateTxn != readTxn);
        prepare(readTxn);
        try {
            commit(updateTxn); // users cannot use reference to update transaction that have been downgraded
            fail("Exception expected");
        } catch (final InvalidTransactionStateException expected) {}
        commit(readTxn);
        assertTrue(readTxn.isTerminated());
        try {
            assertTrue(updateTxn.isTerminated()); // users cannot use reference to update transaction that have been downgraded
            fail("Exception expected");
        } catch (final InvalidTransactionStateException expected) {}
    }

    @Test
    public void downgradeTransactionFailed() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.createUpdateTransaction(defaultExecutor, createListener);
        final UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.createServiceContainer();
        final ServiceRegistry registry = container.newRegistry();
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder sb = txnController.getServiceContext().addService(registry, serviceName, updateTxn);
        final TestService service = new TestService(serviceName, sb, false);
        sb.setService(service).setMode(ServiceMode.ACTIVE).install();
        service.waitStart();
        assertTrue(service.isUp());
        boolean downgraded = txnController.downgradeTransaction(updateTxn, new CompletionListener<ReadTransaction>());
        assertFalse(downgraded); // UpdateTransaction that modified anything cannot be downgraded to read-only transaction
        container.shutdown(updateTxn);
        prepare(updateTxn);
        commit(updateTxn);
        service.waitStop();
    }

    @Test
    public void outsiderTransaction() {
        final TransactionController outsiderController = TransactionController.createInstance();
        final CompletionListener<UpdateTransaction> listener = new CompletionListener<>();
        outsiderController.createUpdateTransaction(defaultExecutor, listener);
        final UpdateTransaction outsiderTransaction = listener.awaitCompletionUninterruptibly();
        SecurityException expected = null;
        try {
            txnController.canCommit(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            prepare(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            prepare(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            prepare(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            txnController.newTask(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            txnController.newTask(outsiderTransaction, new Executable<Void>() {
                public void execute(ExecuteContext<Void> context) {
                }
            });
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            prepare(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            rollback(outsiderTransaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);

        outsiderController.prepare(outsiderTransaction, null);
        outsiderController.commit(outsiderTransaction, null);
        final UpdateTransaction transaction = newUpdateTransaction();
        txnController.prepare(transaction, null);
        txnController.commit(transaction, null);
    }
}
