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

/**
 * Object with write lock support per transaction.
 * <p>
 * With {@link #lockWrite}, the object is locked under a transaction that is attempting to change the object's state.
 * Once locked, no other transaction can edit the object's state. When the transaction completes, the object is
 * automatically unlocked. If the transaction holding the lock is rolled back, {@link #revert(Object)} is invoked, and
 * the object is reverted to its original state before locked.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
abstract class TransactionalObject {

    protected final TransactionalLock lock = new TransactionalLock();
    private boolean snapshotCreated;

    /**
     * Write locks this object under {@code transaction}. If another transaction holds the lock, this method will block
     * until the object is unlocked.
     * 
     * <p> This operation is idempotent. Unlocking occurs automatically when the transaction is finished.
     *  
     * @param transaction the transaction that is attempting to modify current's object state
     */
    final void lockWrite(final Transaction transaction) {
        assert !Thread.holdsLock(this);
        try {
            lock.lock(transaction);
            configureLockCleaner();
        } catch (DeadlockException e) {
            // TODO review this: isn't there a better way of adding this problem, specifically why do we need
            // a task controller, and how will that look like in the log?
            final Problem problem = new Problem(null, e);
            transaction.getProblemReport().addProblem(problem);
            // TODO: we should return and stop processing completely
        }
    }
    
    private void configureLockCleaner() {
        final Object snapshot;
        synchronized (this) {
            if (snapshotCreated) return;
            snapshotCreated = true;
            snapshot = takeSnapshot();
            writeLocked();
        }
        lock.setCleaner(new TransactionalLock.Cleaner() {
            @Override
            public void clean(final boolean reverted) {
                synchronized (TransactionalObject.this) {
                    writeUnlocked();
                    if (reverted) {
                        revert(snapshot);
                    }
                    snapshotCreated = false;
                }
            }
        });
    }

    /**
     * Takes a snapshot of this transactional object's inner state. Invoked when this object is write locked.
     * @return the snapshot
     */
    abstract Object takeSnapshot();

    /**
     * Reverts this object's inner state to what its original state when it was locked. Invoked during transaction rollback or abort.
     * @param snapshot the snapshot
     */
    abstract void revert(Object snapshot);

    /**
     * Notifies that this object is now write locked. Invoked only once per transaction lock.
     */
    void writeLocked() {}

    /**
     * Notifies that this object is now write unlocked.
     */
    void writeUnlocked() {}
}
