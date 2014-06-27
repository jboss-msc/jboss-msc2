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
import org.jboss.msc.txn.TestExecuteContext;
import org.jboss.msc.txn.TestTaskController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertNotNull;

/**
 * Check how MSC2 handles complex task dependency chains, that could lead to cyclic behavior and result in hangs.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public final class ComplexTaskDependencyChainsTestCase extends AbstractTransactionTest {

    private static TestTaskController<Void> task5Controller;
    private static TestTaskController<Void> task7Controller;

    /**
     * Scenario:
     * <UL>
     * <LI>add 9 tasks: 0, 1, 2, 3, 4, 5, 6, 7, 8 to a transaction</LI>
     * <LI>the dependencies are: 8 -> 7 -> 6 -> 5 -> 4 -> 3 -> 2, 4 -> 2</LI>
     * <LI>tasks 6 and 7 are children of task 3</LI>
     * <LI>commit the transaction</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // installing task0
        final TestExecutable<Void> e0 = new TestExecutable<>("0");
        final TestTaskController<Void> task0Controller = newTask(transaction, e0);
        assertNotNull(task0Controller);
        // installing task2
        final TestExecutable<Void> e2 = new TestExecutable<>("2");
        final TestTaskController<Void> task2Controller = newTask(transaction, e2);
        assertNotNull(task2Controller);
        // installing task3
        final CountDownLatch task5Created = new CountDownLatch(1);
        final CountDownLatch task7Created = new CountDownLatch(1);
        final TestExecutable<Void> parent3e = new TestExecutable<Void>("3") {
            @Override
            public void executeInternal(final TestExecuteContext<Void> ctx) {
                try {
                    task5Created.await();
                } catch (Exception ignore) {
                }
                // installing task6
                final TestExecutable<Void> e6 = new TestExecutable<>("6");
                final TestTaskController<Void> task6Controller = newTask(ctx, e6, task5Controller);
                assertNotNull(task6Controller);
                // installing task7
                final TestExecutable<Void> e7 = new TestExecutable<>("7");
                task7Controller = newTask(ctx, e7, task6Controller);
                assertNotNull(task7Controller);
                task7Created.countDown();
            }
        };
        final TestTaskController<Void> task3Controller = newTask(transaction, parent3e, task2Controller);
        assertNotNull(task3Controller);
        // installing task4
        final TestExecutable<Void> e4 = new TestExecutable<>("4");
        final TestTaskController<Void> task4Controller = newTask(transaction, e4, task2Controller, task3Controller);
        assertNotNull(task4Controller);
        // installing task5
        final TestExecutable<Void> e5 = new TestExecutable<>("5");
        task5Controller = newTask(transaction, e5, task4Controller);
        assertNotNull(task5Controller);
        task5Created.countDown();
        // installing task 8
        try {
            task7Created.await();
        } catch (Exception ignore) {
        }
        final TestExecutable<Void> e8 = new TestExecutable<>("8");
        final TestTaskController<Void> task8Controller = newTask(transaction, e8, task7Controller);
        assertNotNull(task8Controller);
        prepare(transaction);
        commit(transaction);
    }

}
