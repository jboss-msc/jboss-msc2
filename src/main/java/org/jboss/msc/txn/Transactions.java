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
    private static final Transaction[] activeTxns = new Transaction[maxTxns];
    private static final long[] txnDeps = new long[maxTxns];

    private Transactions() {
        // forbidden inheritance
    }

    /**
     * Register transaction.
     * 
     * @param txn new active transaction
     * @throws IllegalStateException if there are too many active transactions
     */
    static synchronized void register(final Transaction txn) throws IllegalStateException {
        for (int i = 0; i < maxTxns; i++) {
            if (activeTxns[i] == null) {
                activeTxns[i] = txn;
                return;
            }
        }
        throw TXN.tooManyActiveTransactions();
    }

    /**
     * Unregister transaction.
     * 
     * @param txn old terminated transaction
     */
    static synchronized void unregister(final Transaction txn) {
        // unregister transaction and clean up its dependencies list
        int txnIndex = -1;
        for (int i = 0; i < maxTxns; i++) {
            if (activeTxns[i] == txn) {
                activeTxns[i] = null;
                txnDeps[i] = 0L;
                txnIndex = i;
                break;
            }
        }
        // clean up transaction dependency for every dependent
        long bit = 1L << txnIndex;
        for (int i = 0; i < maxTxns; i++) {
            txnDeps[i] &= ~bit;
        }
    }

    /**
     * Causes <code>dependent</code> transaction to wait for <code>dependency</code> transaction.
     * If some of the participating transactions have been terminated in the meantime wait will not happen.
     * 
     * @param dependent the dependent
     * @param dependency the dependency
     * @param listener the completion listener 
     * @throws DeadlockException if transactions dependency deadlock was detected
     */
    static void waitFor(final Transaction dependent, final Transaction dependency, final TerminationListener listener) throws TransactionDeadlockException {
        // detect self waits
        if (dependent == dependency) {
            Transaction.safeCallTerminateListener(listener);
            return;
        }
        boolean staleTransactions = true;
        synchronized (Transactions.class) {
            // lookup transaction indices from active transactions
            int dependentIndex = -1, dependencyIndex = -1;
            for (int i = 0; i < maxTxns; i++) {
                if (activeTxns[i] == dependent) {
                    dependentIndex = i;
                } else if (activeTxns[i] == dependency) {
                    dependencyIndex = i;
                }
                if (dependentIndex >= 0 && dependencyIndex >= 0) {
                    break; // we have both indices
                }
            }
            // ensure transaction indices are still valid
            if (dependentIndex != -1 && dependencyIndex != -1) {
                staleTransactions = false;
                // register transactions dependency and detect deadlock
                try {
                    addDependency(dependentIndex, dependencyIndex);
                    checkDeadlock(dependentIndex, 0L);
                } catch (final TransactionDeadlockException e) {
                    removeDependency(dependentIndex, dependencyIndex);
                    throw e;
                }
            }
        }
        if (staleTransactions) {
            // Some of participating transactions have been terminated so we're done
            Transaction.safeCallTerminateListener(listener);
        } else {
            // transactions dependency have been registered and no deadlock was detected, registering termination listener
            final OneShotTerminationListener oneShotCompletionListener = OneShotTerminationListener.wrap(listener);
            dependency.addTerminationListener(oneShotCompletionListener);
            dependent.addTerminationListener(oneShotCompletionListener);
        }
    }

    static void waitFor(final Transaction dependent, final Transaction dependency) throws TransactionDeadlockException {
        final CountDownLatch signal = new CountDownLatch(1);
        final TerminationListener listener = new TerminationListener() {
            @Override
            public void transactionTerminated() {
                signal.countDown();
            }
        };
        waitFor(dependent, dependency, listener);
        boolean interrupted = false;
        while (true) {
            try {
                signal.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }


    private static void addDependency(final int dependentIndex, final int dependencyIndex) {
        long bit = 1L << dependencyIndex;
        txnDeps[dependentIndex] |= bit;
    }

    private static void removeDependency(final int dependentIndex, final int dependencyIndex) {
        long bit = 1L << dependencyIndex;
        txnDeps[dependentIndex] &= ~bit;
    }

    private static void checkDeadlock(final int txnIndex, long visited) throws TransactionDeadlockException {
        // check deadlock
        final long bit = 1L << txnIndex;
        if (Bits.allAreSet(visited, bit)) {
            throw new TransactionDeadlockException();
        }
        visited |= bit;
        // process transaction dependencies
        long dependencies = txnDeps[txnIndex];
        long dependencyBit;
        int dependencyIndex;
        while (dependencies != 0L) {
            dependencyBit = Long.lowestOneBit(dependencies);
            dependencyIndex = Long.numberOfTrailingZeros(dependencyBit);
            checkDeadlock(dependencyIndex, visited);
            dependencies &= ~dependencyBit;
        }
    }
}
