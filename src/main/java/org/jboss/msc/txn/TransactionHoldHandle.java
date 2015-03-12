/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class for blocking ACTIVE transactions.
 * Transaction will stay ACTIVE until all acquired hold handles are not released.
 * It is useful for letting know when non-service code is working on behalf of a transaction.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TransactionHoldHandle {

    private final AbstractTransaction txn;
    private final Throwable creationStackTrace;
    private final AtomicBoolean released = new AtomicBoolean();

    TransactionHoldHandle(final AbstractTransaction txn) {
        this.txn = txn;
        this.creationStackTrace = new Throwable();
    }

    public void release() {
        if (released.compareAndSet(false, true)) {
            txn.release(this);
        }
    }

    @Override
    public String toString() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        creationStackTrace.printStackTrace(new PrintStream(baos));
        return baos.toString();
    }

}
