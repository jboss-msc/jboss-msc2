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

import org.jboss.msc.txn.AbstractTransactionTest;
import org.jboss.msc.txn.TestExecutable;
import org.jboss.msc.txn.TestTaskController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ThreeParentTasks_WithDeps_NoChildTasks_NoDeps_TxnCommitted_TestCase extends AbstractTransactionTest {

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction committed</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<>();
        final TestTaskController<Void> task0Controller = newTask(transaction, e0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<>();
        final TestTaskController<Void> task1Controller = newTask(transaction, e1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<>();
        final TestTaskController<Void> task2Controller = newTask(transaction, e2, task1Controller);
        assertNotNull(task2Controller);
        // preparing transaction
        prepare(transaction);
        assertCalled(e0);
        assertCalled(e1);
        assertCalled(e2);
        assertCallOrder(e1, e2);
        // committing transaction
        assertTrue(canCommit(transaction));
        commit(transaction);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction committed</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<>();
        final TestTaskController<Void> task0Controller = newTask(transaction, e0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<>();
        final TestTaskController<Void> task1Controller = newTask(transaction, e1);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<>();
        final TestTaskController<Void> task2Controller = newTask(transaction, e2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // preparing transaction
        prepare(transaction);
        assertCalled(e0);
        assertCalled(e1);
        assertCalled(e2);
        assertCallOrder(e0, e2);
        assertCallOrder(e1, e2);
        // committing transaction
        assertTrue(canCommit(transaction));
        commit(transaction);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1</LI>
     * <LI>no children</LI>
     * <LI>transaction committed</LI>
     * </UL>
     */
    @Test
    public void usecase3() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<>();
        final TestTaskController<Void> task0Controller = newTask(transaction, e0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<>();
        final TestTaskController<Void> task1Controller = newTask(transaction, e1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<>();
        final TestTaskController<Void> task2Controller = newTask(transaction, e2, task1Controller);
        assertNotNull(task2Controller);
        // preparing transaction
        prepare(transaction);
        assertCalled(e0);
        assertCalled(e1);
        assertCalled(e2);
        assertCallOrder(e0, e1, e2);
        // committing transaction
        assertTrue(canCommit(transaction));
        commit(transaction);
    }

    /**
     * Scenario:
     * <UL>
     * <LI>task0 completes at EXECUTE</LI>
     * <LI>task1 completes at EXECUTE, depends on task0</LI>
     * <LI>task2 completes at EXECUTE, depends on task1 and task0</LI>
     * <LI>no children</LI>
     * <LI>transaction committed</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<>();
        final TestTaskController<Void> task0Controller = newTask(transaction, e0);
        assertNotNull(task0Controller);
        // installing task1
        final TestExecutable<Void> e1 = new TestExecutable<>();
        final TestTaskController<Void> task1Controller = newTask(transaction, e1, task0Controller);
        assertNotNull(task1Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<>();
        final TestTaskController<Void> task2Controller = newTask(transaction, e2, task0Controller, task1Controller);
        assertNotNull(task2Controller);
        // preparing transaction
        prepare(transaction);
        assertCalled(e0);
        assertCalled(e1);
        assertCalled(e2);
        assertCallOrder(e0, e1, e2);
        // committing transaction
        assertTrue(canCommit(transaction));
        commit(transaction);
    }
}
