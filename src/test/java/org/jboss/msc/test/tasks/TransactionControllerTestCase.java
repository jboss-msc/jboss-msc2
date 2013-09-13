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

import static org.junit.Assert.assertNotNull;

import org.jboss.msc.test.utils.AbstractTransactionTest;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.DeadlockException;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.TransactionController;
import org.junit.Test;

/**
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class TransactionControllerTestCase extends AbstractTransactionTest {

    // TODO add more tests to this class

    @Test
    public void outsiderTransaction() throws InterruptedException, DeadlockException {
        final TransactionController outsiderController = TransactionController.createInstance();
        final BasicTransaction outsiderTransaction = outsiderController.create(defaultExecutor);
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
                public void execute(ExecuteContext<Void> context) {}});
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

        expected = null;
        final BasicTransaction transaction = newTransaction();
        try {
            txnController.waitFor(outsiderTransaction, transaction);
        } catch (SecurityException e) {
            expected = e;
        }
        assertNotNull(expected);
        outsiderController.prepare(outsiderTransaction, null);
        outsiderController.commit(outsiderTransaction, null);
        txnController.prepare(transaction, null);
        txnController.commit(transaction, null);
    }
}
