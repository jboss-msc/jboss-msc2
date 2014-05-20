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

import static org.jboss.msc._private.MSCLogger.TXN;

import java.util.concurrent.CountDownLatch;

/**
 * Shared thread-safe utility class that keeps track of active transactions and their dependencies.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class Transactions {

    private static final int maxTxns = 64;
    private static int activeTxns;

    private Transactions() {
        // forbidden inheritance
    }

    /**
     * Increment active transaction count.
     * 
     * @throws IllegalStateException if there are too many active transactions
     */
    static synchronized void register() throws IllegalStateException {
        if (activeTxns == 64) throw TXN.tooManyActiveTransactions();
        activeTxns++;
    }

    /**
     * Decrement active transaction count.
     */
    static synchronized void unregister() throws IllegalStateException {
        assert activeTxns > 0;
        activeTxns--;
    }

}
