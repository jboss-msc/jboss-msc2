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
public final class OneParentTask_NoDeps_OneChildTask_NoDeps_TxnCommitted_TestCase extends AbstractTransactionTest {

    /**
     * Scenario:
     * <UL>
     * <LI>parent task completes at EXECUTE</LI>
     * <LI>child task completes at EXECUTE</LI>
     * <LI>transaction committed</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // preparing child task
        final TestExecutable<Void> e1 = new TestExecutable<Void>();
        final TestRevertible r1 = new TestRevertible();
        // installing parent task
        final TestExecutable<Void> e0 = new TestExecutable<Void>() {
            @Override
            public void executeInternal(final ExecuteContext<Void> ctx) {
                // installing child task
                final TaskController<Void> childController = newTask(ctx, e1, r1);
                assertNotNull(childController);
            }
        };
        final TestRevertible r0 = new TestRevertible();
        final TaskController<Void> parentController = newTask(transaction, e0, r0);
        assertNotNull(parentController);
        // preparing transaction
        prepare(transaction);
        assertCalled(e0);
        assertNotCalled(r0);
        assertCalled(e1);
        assertNotCalled(r1);
        assertCallOrder(e0, e1);
        // committing transaction
        assertTrue(canCommit(transaction));
        commit(transaction);
    }
}
