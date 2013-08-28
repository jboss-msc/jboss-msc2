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

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class SynchronousRollbackListener<T extends Transaction> implements RollbackListener<T> {
    private volatile RollbackResult<T> result;

    public void handleEvent(final RollbackResult<T> subject) {
        synchronized (this) {
            result = subject;
            notifyAll();
        }
    }

    public RollbackResult<T> await() throws InterruptedException {
        RollbackResult<T> result = this.result;
        if (result != null) {
            return result;
        }
        synchronized (this) {
            while (result == null) {
                wait();
            }
            return result;
        }
    }

    public RollbackResult<T> awaitUninterruptibly() {
        RollbackResult<T> result = this.result;
        if (result != null) {
            return result;
        }
        boolean intr = false;
        try {
            synchronized (this) {
                while (result == null) try {
                    wait();
                } catch (InterruptedException e) {
                    intr = true;
                }
                return result;
            }
        } finally {
            if (intr) Thread.currentThread().interrupt();
        }
    }
}
