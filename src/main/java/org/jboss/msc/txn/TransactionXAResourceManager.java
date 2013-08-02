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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TransactionXAResourceManager {

    private static final ConcurrentMap<UUID, TransactionXAResourceManager> RM_MAP = new ConcurrentHashMap<>();
    private final ConcurrentMap<XidKey, XATransaction> incompleteTransactions = new ConcurrentHashMap<>();
    private final TransactionController transactionController;
    private final UUID uuid = UUID.randomUUID();

    TransactionXAResourceManager(final TransactionController transactionController) {
        this.transactionController = transactionController;
    }

    /**
     * Create a new XA resource to use with this resource manager.  The XA resource may be used to enlist
     * in a transaction, or to perform recovery tasks.
     *
     * @param taskExecutor the task executor to use for transactions created by this resource
     * @param maxSeverity the maximum severity to configure for transactions created by this resource
     * @return a new XA resource
     */
    public XAResource createXAResource(final Executor taskExecutor, final Problem.Severity maxSeverity) {
        return new TransactionXAResource(this, taskExecutor, maxSeverity);
    }

    /**
     * Create a new XA resource which can only be used for recovering and completing transactions, not initiating them.
     *
     * @return a new XA resource
     */
    public XAResource createRecoveryXAResource() {
        return new TransactionXAResource(this, null, null);
    }

    /**
     * Get the existing transaction with the given XID.  If no such transaction exists, {@code null} is returned.
     *
     * @return the existing transaction with the given XID or {@code null} if none exist
     */
    XATransaction getTransaction(Xid xid) {
        return incompleteTransactions.get(new XidKey(xid));
    }

    XATransaction removeTransaction(final Xid xid) {
        return incompleteTransactions.remove(new XidKey(xid));
    }

    /**
     * Attempt to register a new transaction with the given XID.
     *
     * @param xid the XID
     * @param transaction the transaction
     * @return {@code true} if the registration succeeded, {@code false} if a transaction already existed with that ID
     */
    boolean registerTransaction(Xid xid, XATransaction transaction) {
        return incompleteTransactions.putIfAbsent(new XidKey(xid), transaction) == null;
    }

    boolean unregisterTransaction(final Xid xid, final XATransaction transaction) {
        return incompleteTransactions.remove(new XidKey(xid), transaction);
    }

    Xid[] recover() {
        // approximate size
        final List<Xid> list = new ArrayList<>(incompleteTransactions.size());
        for (Map.Entry<XidKey, XATransaction> entry : incompleteTransactions.entrySet()) {
            list.add(entry.getKey().getXid());
        }
        return list.toArray(new Xid[list.size()]);
    }

    TransactionController getController() {
        return transactionController;
    }

    UUID getUuid() {
        return uuid;
    }

    static TransactionXAResource getSerialized(final UUID uuid) {
        final TransactionXAResourceManager resourceManager = RM_MAP.get(uuid);
        if (resourceManager == null) return null;
        return new TransactionXAResource(resourceManager, null, null);
    }

    static final class XidKey {
        private final Xid xid;
        private final int hashCode;

        XidKey(final Xid xid) {
            hashCode = (Arrays.hashCode(xid.getGlobalTransactionId()) * 17 + Arrays.hashCode(xid.getBranchQualifier())) * 17 + xid.getFormatId();
            this.xid = xid;
        }

        public Xid getXid() {
            return xid;
        }

        public boolean equals(Object other) {
            return other instanceof XidKey && equals((XidKey) other);
        }

        public boolean equals(XidKey other) {
            return other == this || other != null
                    && hashCode == other.hashCode
                    && xid == other.xid
                    || (xid.getFormatId() == other.xid.getFormatId()
                        && Arrays.equals(xid.getGlobalTransactionId(), other.xid.getGlobalTransactionId())
                        && Arrays.equals(xid.getBranchQualifier(), other.xid.getBranchQualifier()));
        }

        public int hashCode() {
            return hashCode;
        }
    }


}
