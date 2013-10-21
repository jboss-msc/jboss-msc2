/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

/**
 * A context that allows to acquire transaction aware locks.
 * 
 * <p>Once the lock is acquired it will be automatically released when transaction will be terminated i.e.
 * either after transaction rollback, commit or abort phase completion.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface LockingContext {

    /**
     * Transaction acquires the lock asynchronously.
     * Completion listener is called upon lock retrieval.
     *
     * <p>If the lock is not available then the current transaction becomes
     * inactive and lies dormant until the lock is acquired by the current transaction.
     * 
     * @param lock the lock to acquire
     * @param listener lock request completion listener
     */
    void lockAsynchronously(TransactionalLock lock, LockListener listener);

    /**
     * Transaction acquires the lock only if it is free at the time of invocation.
     *
     * <p>Acquires the lock if it is available and returns immediately
     * with the value {@code true}.
     * If the lock is not available then this method will return
     * immediately with the value {@code false}.
     *
     * @param lock the lock to acquire
     * @return {@code true} if the lock was acquired and
     *         {@code false} otherwise
     */
    boolean tryLock(TransactionalLock lock);

}
