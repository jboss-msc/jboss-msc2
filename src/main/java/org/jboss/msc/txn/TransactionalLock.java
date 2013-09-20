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

/**
 * Transaction aware lock.
 * 
 * <p>Only one transaction at a time can own this lock.
 * If the lock is not available then the current transaction becomes
 * inactive and lies dormant until transactional lock is freed.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TransactionalLock {

    private static final AtomicReferenceFieldUpdater<TransactionalLock, Transaction> ownerUpdater = AtomicReferenceFieldUpdater.newUpdater(TransactionalLock.class, Transaction.class, "owner");
    private volatile Transaction owner;
    
    TransactionalLock() {
    }
    
    boolean lock(final Transaction newOwner) throws InterruptedException, DeadlockException {
        Transaction previousOwner;
        do {
            previousOwner = owner;
            if (previousOwner == newOwner) {
                return true; // reentrant access
            }
            if (previousOwner != null) {
                Transactions.waitFor(newOwner, previousOwner);
            }
        } while (!ownerUpdater.compareAndSet(this, null, newOwner));
        newOwner.addLock(this);
        return false;
    }
    
    boolean tryLock(final Transaction newOwner) {
        Transaction previousOwner;
        do {
            previousOwner = owner;
            if (previousOwner == newOwner) {
                return true; // reentrant access
            }
            if (previousOwner != null) {
                return false;
            }
        } while (!ownerUpdater.compareAndSet(this, null, newOwner));
        newOwner.addLock(this);
        return true;
    }
    
    void unlock(final Transaction currentOwner) {
        if (!ownerUpdater.compareAndSet(this, currentOwner, null)) {
            // should never happen
        }
    }
    
    boolean isOwnedBy(final Transaction txn) {
        return owner == txn;
    }
    
    Transaction getOwner() {
        return owner;
    }

}
