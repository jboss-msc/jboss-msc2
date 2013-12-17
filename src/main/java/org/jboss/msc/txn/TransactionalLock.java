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

package org.jboss.msc.txn;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.msc._private.MSCLogger;

/**
 * Transaction aware lock.
 * 
 * <p>Only one transaction at a time can own this lock.
 * If the lock is not available then the current transaction becomes
 * inactive and lies dormant until transactional lock is freed.
 * <p>
 * It is possible to associate cleanup task via {@link #setCleaner(Cleaner)}
 * with this lock that will be executed before the lock is freed.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TransactionalLock {

    private static final AtomicReferenceFieldUpdater<TransactionalLock, Transaction> ownerUpdater = AtomicReferenceFieldUpdater.newUpdater(TransactionalLock.class, Transaction.class, "owner");
    private volatile Transaction owner;
    private static final AtomicReferenceFieldUpdater<TransactionalLock, Cleaner> cleanerUpdater = AtomicReferenceFieldUpdater.newUpdater(TransactionalLock.class, Cleaner.class, "cleaner");
    private volatile Cleaner cleaner;
    
    TransactionalLock() {}
    
    void lockAsynchronously(final Transaction newOwner, final LockListener listener) {
        Transaction previousOwner;
        while (true) {
            previousOwner = owner;
            if (previousOwner == null) {
                if (ownerUpdater.compareAndSet(this, null, newOwner)) {
                    // lock successfully acquired
                    newOwner.addLock(this);
                    safeCallLockAcquired(listener);
                    break;
                }
            } else {
                if (previousOwner == newOwner) {
                    // reentrant access
                    safeCallLockAcquired(listener);
                    break;
                } else {
                    // some transaction already owns the lock, registering termination listener
                    final boolean deadlockDetected = Transactions.waitForAsynchronously(newOwner, previousOwner,
                            new TerminationListener() {
                                @Override
                                public void transactionTerminated() {
                                    lockAsynchronously(newOwner, listener);
                                }
                            });
                    if (deadlockDetected) safeCallDeadlockDetected(listener); 
                    break;
                }
            }
        }
    }

    boolean tryLock(final Transaction newOwner) {
        if (!ownerUpdater.compareAndSet(this, null, newOwner)) return false;
        newOwner.addLock(this);
        return true;
    }
    
    void setCleaner(final Cleaner cleaner) {
        while (cleaner != null) if (cleanerUpdater.compareAndSet(this, null, cleaner)) return;
    }
    
    @SuppressWarnings("finally")
    void unlock(final Transaction currentOwner, final boolean reverted) {
        if (!ownerUpdater.compareAndSet(this, currentOwner, null)) return;
        final Cleaner cleaner = this.cleaner;
        if (cleaner != null) {
            try {
                cleaner.clean(reverted);
            } catch (final Throwable t) {
                MSCLogger.FAIL.lockCleanupFailed(t);
            } finally {
                while (true) if (cleanerUpdater.compareAndSet(this, cleaner, null)) return;
            }
        }
    }
    
    private void safeCallLockAcquired(final LockListener listener) {
        try {
            listener.lockAcquired();
        } catch (final Throwable t) {
            MSCLogger.FAIL.lockListenerFailed(t);
        }
    }

    private void safeCallDeadlockDetected(final LockListener listener) {
        try {
            listener.deadlockDetected();
        } catch (final Throwable t) {
            MSCLogger.FAIL.lockListenerFailed(t);
        }
    }

    boolean isOwnedBy(final Transaction txn) {
        return owner == txn;
    }
    
    static interface Cleaner {
        void clean(boolean reverted);
    }

}
