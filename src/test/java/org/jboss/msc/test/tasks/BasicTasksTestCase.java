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
import org.jboss.msc.test.utils.TrackingTask;
import org.jboss.msc.txn.ExecuteContext;
import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.TaskBuilder;
import org.jboss.msc.txn.TaskController;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class BasicTasksTestCase extends AbstractTransactionTest {

    @Test
    public void emptyTransactionPrepareCommit() {
        final UpdateTransaction transaction = newUpdateTransaction();
        prepare(transaction);
        commit(transaction);
    }

    @Test
    public void testCommitFromListener() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // install task
        final TrackingTask task = new TrackingTask();
        final TaskBuilder<Object> taskBuilder = txnController.newTask(transaction, task);
        final TaskController<Object> controller = taskBuilder.release();
        // prepare and commit transaction from listener
        prepareAndCommitFromListener(transaction);
        // asserts
        assertTrue(task.isExecuted());
        assertEquals(controller.getTransaction(), transaction);
        controller.getResult();
    }

    @Test
    public void testSimplePrepareCommit() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // install task
        final TrackingTask task = new TrackingTask();
        final TaskBuilder<Object> taskBuilder = txnController.newTask(transaction, task);
        final TaskController<Object> controller = taskBuilder.release();
        // prepare transaction
        prepare(transaction);
        // commit transaction
        commit(transaction);
        // asserts
        assertTrue(task.isExecuted());
        assertEquals(controller.getTransaction(), transaction);
        controller.getResult();
    }

    @Test
    public void testSimpleChildren() {
        final UpdateTransaction transaction = newUpdateTransaction();
        class Task extends TrackingTask {
            private final int n;
            private final int d;

            Task(final int n, final int d) {
                this.n = n;
                this.d = d;
            }

            @Override
            public void execute(final ExecuteContext<Object> context) {
                if (d > 0)
                    for (int i = 0; i < n; i++) {
                        Task task = new Task(n, d - 1);
                        context.newTask(task).release();
                    }
                super.execute(context);
            }
        }
        // install task
        Task task = new Task(3, 4);
        final TaskBuilder<Object> taskBuilder = txnController.newTask(transaction, task);
        final TaskController<Object> controller = taskBuilder.release();
        // prepare and commit transaction from listener
        prepareAndCommitFromListener(transaction);
        // asserts
        assertTrue(task.isExecuted());
        assertEquals(controller.getTransaction(), transaction);
        controller.getResult();
    }

    @Test
    public void installNewTaskToPreparedTransaction() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // install task 1
        final TrackingTask task1 = new TrackingTask();
        final TaskBuilder<Object> taskBuilder = txnController.newTask(transaction, task1);
        final TaskController<Object> controller = taskBuilder.release();
        // prepare transaction
        prepare(transaction);
        // install task 2 - should fail because transaction have been prepared
        final TrackingTask task2 = new TrackingTask();
        try {
            txnController.newTask(transaction, task2).release();
            fail("cannot add new tasks to prepared transaction");
        } catch (InvalidTransactionStateException expected) {
        }
        // commit transaction
        commit(transaction);
        // asserts
        assertTrue(task1.isExecuted());
        assertFalse(task2.isExecuted());
        assertEquals(controller.getTransaction(), transaction);
        controller.getResult();
    }

    @Test
    public void installNewTaskToCommitedTransaction() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // install task 1
        final TrackingTask task1 = new TrackingTask();
        final TaskBuilder<Object> taskBuilder = txnController.newTask(transaction, task1);
        final TaskController<Object> controller = taskBuilder.release();
        // prepare and commit transaction from listener
        prepareAndCommitFromListener(transaction);
        // install task 2 - should fail because transaction have been commited
        final TrackingTask task2 = new TrackingTask();
        try {
            txnController.newTask(transaction, task2).release();
            fail("cannot add new tasks to committed transaction");
        } catch (InvalidTransactionStateException expected) {
        }
        // asserts
        assertTrue(task1.isExecuted());
        assertFalse(task2.isExecuted());
        assertEquals(controller.getTransaction(), transaction);
        controller.getResult();
    }

    @Test
    public void simpleDependencies() {
        final UpdateTransaction transaction = newUpdateTransaction();
        // install task
        TrackingTask[][] tasks = new TrackingTask[8][8];
        TaskController<?>[][] controllers = new TaskController<?>[8][8];
        Random r = new Random(492939L);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                final TrackingTask task = new TrackingTask();
                tasks[i][j] = task;
                final TaskBuilder<Object> builder = txnController.newTask(transaction, task);
                if (i > 0) {
                    int x = r.nextInt();
                    for (int b = 0; b < 8; b++) {
                        if ((x & (1 << b)) != 0) {
                            builder.addDependency(controllers[i - 1][b]);
                        }
                    }
                }
                controllers[i][j] = builder.release();
            }
        }
        // prepare and commit transaction from listener
        prepareAndCommitFromListener(transaction);
        // asserts
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                final TrackingTask task = tasks[i][j];
                assertTrue(task.isExecuted());
                final TaskController<?> controller = controllers[i][j];
                assertEquals(controller.getTransaction(), transaction);
                controller.getResult();
            }
        }
    }
}
