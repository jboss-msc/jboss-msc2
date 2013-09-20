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
package org.jboss.msc.txn;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Object with write lock support per transaction.
 * <p>
 * With {@link #lockWrite}, the object is locked under a transaction that is attempting to change the object's state.
 * Once locked, no other transaction can edit the object's state. When the transaction completes, the object is
 * automatically unlocked. If the transaction holding the lock is rolled back, {@link #revert(Object)} is invoked, and
 * the object is reverted to its original state before locked.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
abstract class TransactionalObject {

    private static AttachmentKey<Map<TransactionalObject, Object>> TRANSACTIONAL_OBJECTS = AttachmentKey.create();
    private static AttachmentKey<TaskController<Void>> UNLOCK_TASK = AttachmentKey.create();

    private TransactionalLock lock = new TransactionalLock();

    /**
     * Write locks this object under {@code transaction}. If another transaction holds the lock, this method will block
     * until the object is unlocked.
     * 
     * <p> This operation is idempotent. Unlocking occurs automatically when the transaction is finished.
     *  
     * @param transaction the transaction that is attempting to modify current's object state
     * @param taskFactory the  task factory
     */
    final void lockWrite(final Transaction transaction, final TaskFactory taskFactory) {
        assert !Thread.holdsLock(this);
        try {
            if (lock.lock(transaction)) {
                return; // reentrant locking
            }
        } catch (DeadlockException e) {
            // TODO review this: isn't there a better way of adding this problem, specifically why do we need
            // a task controller, and how will that look like in the log?
            final Problem problem = new Problem(null, e);
            transaction.getProblemReport().addProblem(problem);
            // TODO: we should return and stop processing completely
        } catch (InterruptedException e) {
            // ignored
        }
        final Object snapshot;
        synchronized (this) {
            snapshot = takeSnapshot();
            // notice that write locked must be garanteed to have been invoked if/when
            // another thread checks that current lock is not null
            writeLocked(transaction);
        }
        final Map<TransactionalObject, Object> transactionalObjects;
        synchronized (TRANSACTIONAL_OBJECTS) {
            if (transaction.hasAttachment(TRANSACTIONAL_OBJECTS)) {
                transactionalObjects = transaction.getAttachment(TRANSACTIONAL_OBJECTS);
            } else {
                transactionalObjects = new HashMap<TransactionalObject, Object>();
                transaction.putAttachment(TRANSACTIONAL_OBJECTS, transactionalObjects);
                transaction.putAttachment(UNLOCK_TASK, taskFactory.newTask().setTraits(new UnlockWriteTask(transactionalObjects)).release());
            }
        }
        transactionalObjects.put(this, snapshot);
    }

    /**
     * Indicates if this object is locked by {@code transaction}.
     * 
     * @param transaction an active transaction
     * @return {@code true} only if this object is locked by {@code transaction}.
     */
    synchronized final boolean isWriteLocked(Transaction transaction) {
        return lock.isOwnedBy(transaction);
    }

    /**
     * For proper unlocking and revert of this object's state, every task that affects all locked objects at current
     * transaction must depend on the unlock task.
     * 
     * As there is only one unlock task per transaction, it is safe to obtain the unlock task from only one affected
     * TransactionalObject, even if the task edits other TransactionalObjects.
     * 
     * @return the unlock task
     */
    TaskController<Void> getUnlockTask() {
        return lock.getOwner().getAttachment(UNLOCK_TASK);
    }

    private final void unlockWrite() {
        assert Thread.holdsLock(this);
        writeUnlocked();
    }

    /**
     * Takes a snapshot of this transactional object's inner state. Invoked when this object is write locked.
     * 
     * @return the snapshot
     */
    abstract Object takeSnapshot();

    /**
     * Reverts this object's inner state to what its original state when it was locked. Invoked during transaction
     * rollback or abort.
     * 
     * @param snapshot the snapshot
     */
    abstract void revert(Object snapshot);

    /**
     * Performs validation of new objects state for active transaction.
     * 
     * @param context every validation problem found should be added to this context
     */
    void validate(ReportableContext context) {}

    /**
     * Notifies that this object is now write locked. Invoked only once per transaction lock.
     * 
     * @param transaction the transaction under which this object is locked
     * @param context     the service context
     */
    void writeLocked(Transaction transaction) {}

    /**
     * Notifies that this object is now write unlocked.
     */
    void writeUnlocked() {}

    private static class UnlockWriteTask implements Validatable, Committable, Revertible {

        private Map<TransactionalObject, Object> transactionalObjects;

        private UnlockWriteTask(Map<TransactionalObject, Object> transactionalObjects) {
            this.transactionalObjects = transactionalObjects;
        }

        @Override
        public void validate(ValidateContext context) {
            try {
                for (TransactionalObject transactionalObject: transactionalObjects.keySet()) {
                    synchronized (transactionalObject) {
                        transactionalObject.validate(context);
                    }
                }
            } finally {
                context.complete();
            }
        }

        @Override
        public void rollback(RollbackContext context) {
            try {
                for (Entry<TransactionalObject, Object> entry: transactionalObjects.entrySet()) {
                    final TransactionalObject transactionalObject = entry.getKey();
                    final Object snapshot = entry.getValue();
                    synchronized (transactionalObject) {
                        transactionalObject.unlockWrite();
                        if (snapshot != null) {
                            transactionalObject.revert(snapshot);
                        }
                    }
                }
            } finally {
                context.complete();
            }
        }

        @Override
        public void commit(CommitContext context) {
            try {
                for (TransactionalObject transactionalObject: transactionalObjects.keySet()) {
                    synchronized (transactionalObject) {
                        transactionalObject.unlockWrite();
                    }
                }
            } finally {
                context.complete();
            }
        }
    }

}
