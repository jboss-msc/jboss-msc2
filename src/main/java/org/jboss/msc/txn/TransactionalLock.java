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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.jboss.msc._private.MSCLogger;

/**
 * Transaction aware lock.
 * 
 * <p>Only one transaction at a time can own this lock.
 * If the lock is not available then the current transaction becomes
 * inactive and lies dormant until transactional lock is freed.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
// TODO: eliminate this class
public final class TransactionalLock {

    private static final AtomicReferenceFieldUpdater<TransactionalLock, Transaction> ownerUpdater = AtomicReferenceFieldUpdater.newUpdater(TransactionalLock.class, Transaction.class, "owner");
    private volatile Transaction owner;
    private final Cleaner cleaner;

    TransactionalLock() {
        this(null);
    }

    TransactionalLock(Cleaner cleaner) {
        this.cleaner = cleaner;
    }

    void lock(final Transaction newOwner) {
        if (newOwner.isTerminated()) {
            throw MSCLogger.TXN.txnTerminated();
        }
        final Transaction previousOwner = ownerUpdater.get(this);
        if (previousOwner == newOwner) return;
        if (!ownerUpdater.compareAndSet(this, null, newOwner)) throw new UnsupportedOperationException();
        newOwner.addLock(this);
    }

    void unlock(final Transaction currentOwner) {
        if (!ownerUpdater.compareAndSet(this, currentOwner, null)) throw new UnsupportedOperationException();
        final Cleaner cleaner = this.cleaner;
        if (cleaner != null) {
            try {
                cleaner.clean();
            } catch (final Throwable t) {
                MSCLogger.FAIL.lockCleanupFailed(t);
            }
        }
    }

    boolean isOwnedBy(final Transaction txn) {
        return owner == txn;
    }

    static interface Cleaner {
        void clean();
    }

}
