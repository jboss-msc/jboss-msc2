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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Transaction completion utility listener that allows synchronized wait for transaction phase to be completed.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * 
 * @param <R> transaction result type.
 */
public final class CompletionListener<R> implements Listener<R> {

    private final CountDownLatch latch = new CountDownLatch(1);

    private volatile R result;

    /**
     * {@inheritDoc}
     */
    public void handleEvent(final R result) {
        this.result = result;
        latch.countDown();
    }

    /**
     * Awaits transaction phase result interruptibly.
     * @return transaction phase result
     * @throws InterruptedException if waiting thread have been interrupted
     */
    public R awaitCompletion() throws InterruptedException {
        if (result != null) return result;
        latch.await();
        return result;
    }

    /**
     * Awaits transaction phase result interruptibly in the specified time limit.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return transaction phase result
     * @throws InterruptedException if waiting thread have been interrupted
     * @throws TimeoutException if timed out
     */
    public R awaitCompletion(final long timeout, final TimeUnit unit) throws InterruptedException, TimeoutException {
        if (result != null) return result;
        if (!latch.await(timeout, unit)) {
            throw new TimeoutException();
        }
        return result;
    }

    /**
     * Awaits transaction phase result uninterruptibly.
     * Thread interruption doesn't cause this method to return immediately.
     * Instead the interruption attempts are tracked and later propagated to the current thread prior method return.  
     * @return transaction phase result
     */
    public R awaitCompletionUninterruptibly() {
        if (result != null) return result;
        boolean intr = false;
        try {
            while (true) {
                try {
                    latch.await();
                    break;
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
            return result;
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }

    /**
     * Awaits transaction phase result uninterruptibly in the specified time limit.
     * Thread interruption doesn't cause this method to return immediately.
     * Instead the interruption attempts are tracked and later propagated to the current thread prior method return.
     * @param timeout the maximum time to wait
     * @param unit the time unit of the {@code timeout} argument
     * @return transaction phase result
     * @throws TimeoutException if timed out
     */
    public R awaitCompletionUninterruptibly(final long timeout, final TimeUnit unit) throws InterruptedException, TimeoutException {
        if (result != null) return result;
        boolean intr = false;
        try {
            long now = System.nanoTime();
            long remaining = unit.toNanos(timeout);
            while (true) {
                if (remaining <= 0L) {
                    throw new TimeoutException();
                }
                try {
                    if (latch.await(remaining, TimeUnit.NANOSECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    intr = true;
                } finally {
                    remaining -= (-now + (now = System.nanoTime()));
                }
            }
            return result;
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }
}
