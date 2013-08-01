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

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * The MSC XA resource implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TransactionXAResource extends TransactionManagementScheme<XATransaction> implements XAResource {

    private static final AttachmentKey<XidKey> XID_KEY = AttachmentKey.create();

    private static final Xid[] NO_XIDS = new Xid[0];
    private static final ConcurrentMap<UUID, TransactionXAResource> XA_RESOURCE_MAP = new ConcurrentHashMap<>();
    private static final AttachmentKey<TransactionXAResource> KEY = AttachmentKey.create();

    private final ConcurrentMap<XidKey, XATransaction> incompleteTransactions = new ConcurrentHashMap<>();
    private final UUID uuid = UUID.randomUUID();
    private final TransactionController transactionController;
    private final ThreadLocal<Result> propagation = new ThreadLocal<>();

    TransactionXAResource(TransactionController transactionController) {
        this.transactionController = transactionController;
    }

    /**
     * Create an XA transaction and enlist it with the transaction manager's current transaction.
     *
     * @param transactionController the transaction controller to create transactions from
     * @param transactionManager the transaction manager to enlist with
     * @param taskExecutor the task executor
     * @param maxSeverity the maximum problem severity to allow
     * @return the transaction
     * @throws SystemException if the enlistment failed
     * @throws RollbackException if the rollback failed
     */
    public static XATransaction createTransaction(TransactionController transactionController, TransactionManager transactionManager, Executor taskExecutor, Problem.Severity maxSeverity) throws SystemException, RollbackException {
        return transactionController.registerTransaction(getXAResource(transactionController).createTransaction(transactionManager, taskExecutor, maxSeverity));
    }

    /**
     * Get the XA resource associated with a transaction controller.  The resultant resource can be used to enlist
     * transactions in a JTA transaction manager.
     *
     * @return the XA resource associated with the transaction controller
     */
    static TransactionXAResource getXAResource(TransactionController controller) {
        TransactionXAResource xaResource = controller.getAttachmentIfPresent(KEY);
        if (xaResource == null) {
            xaResource = new TransactionXAResource(controller);
            TransactionXAResource appearing = controller.putAttachmentIfAbsent(KEY, xaResource);
            if (appearing != null) {
                xaResource = appearing;
            }
        }
        return xaResource;
    }

    XATransaction createTransaction(TransactionManager transactionManager, Executor taskExecutor, Problem.Severity maxSeverity) throws SystemException, RollbackException {
        propagation.set(new Result(taskExecutor, maxSeverity));
        try {
            if (! transactionManager.getTransaction().enlistResource(this)) {
                throw new IllegalStateException("Transaction manager failed to enlist the transaction");
            }
            XATransaction transaction = propagation.get().transaction;
            if (transaction == null) {
                throw new IllegalStateException("Transaction Manager did not associate transaction");
            }
            return transaction;
        } finally {
            propagation.remove();
        }
    }

    public boolean isSameRM(final XAResource resource) throws XAException {
        return resource instanceof TransactionXAResource && isSameRM((TransactionXAResource) resource);
    }

    public boolean isSameRM(final TransactionXAResource resource) throws XAException {
        return resource != null && transactionController == resource.transactionController;
    }

    public Xid[] recover(final int flags) throws XAException {
        switch (flags) {
            case TMNOFLAGS:
            case TMENDRSCAN: return NO_XIDS;
            case TMSTARTRSCAN: break;
            default: throw new XAException(XAException.XAER_INVAL);
        }
        List<Xid> list = new ArrayList<>(incompleteTransactions.size());
        for (Map.Entry<XidKey, XATransaction> entry : incompleteTransactions.entrySet()) {
            list.add(entry.getKey().getXid());
        }
        return list.toArray(new Xid[list.size()]);
    }

    public void start(final Xid xid, final int flags) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        XidKey key = new XidKey(xid);
        XATransaction transaction = incompleteTransactions.get(key);
        if (flags == TMRESUME) {
            // verify that a transaction is indeed associated
            if (transaction == null) {
                throw new XAException(XAException.XAER_NOTA);
            }
        } else if (flags == TMJOIN || flags == TMNOFLAGS) {
            if (transaction != null) {
                throw new XAException(XAException.XAER_DUPID);
            }
            // create new transaction and associate it
            final Result result = propagation.get();
            if (result == null) {
                // todo - find a better error code
                throw new XAException(XAException.XAER_INVAL);
            }
            transaction = new XATransaction(transactionController, result.taskExecutor, result.maxSeverity);
            result.transaction = transaction;
            XidKey appearing;
            if ((appearing = transaction.putAttachmentIfAbsent(XID_KEY, key)) != null) {
                if (! appearing.equals(key)) {
                    // transaction is already associated with a different Xid...
                    throw new XAException(XAException.XAER_INVAL);
                }
            }
            final XATransaction appearingTransaction = incompleteTransactions.putIfAbsent(key, transaction);
            if (appearingTransaction != null) {
                throw new XAException(XAException.XAER_DUPID);
            }
        }
        if (transaction != null && flags != TMJOIN && flags != TMRESUME) {
            throw new XAException(XAException.XAER_DUPID);
        }
    }

    public void end(final Xid xid, final int flags) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        XidKey key = new XidKey(xid);
        Transaction transaction = incompleteTransactions.get(key);
        if (transaction == null) {
            throw new XAException(XAException.XAER_NOTA);
        }
        if (flags == TMFAIL) {
            final SynchronousListener<Transaction> listener = new SynchronousListener<>();
            transaction.rollback(listener);
            listener.awaitUninterruptibly();
            // todo check rollback result..?
        }
    }

    public void forget(final Xid xid) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        XidKey key = new XidKey(xid);
        Transaction transaction = incompleteTransactions.remove(key);
        if (transaction != null) try {
            final SynchronousListener<Transaction> listener = new SynchronousListener<>();
            transaction.rollback(listener);
            listener.awaitUninterruptibly();
            // todo check rollback result..?
        } catch (TransactionRolledBackException ignored) {
        } catch (InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public int getTransactionTimeout() throws XAException {
        return Integer.MAX_VALUE;
    }

    public int prepare(final Xid xid) throws XAException {
        Transaction transaction = getTransaction(xid);
        try {
            final SynchronousListener<Transaction> listener = new SynchronousListener<>();
            transaction.prepare(listener);
            listener.awaitUninterruptibly();
            if (transaction.canCommit()) {
                // todo - a way to establish whether changes were made?
                return XA_OK;
            } else {
                throw new XAException(XAException.XA_RBROLLBACK);
            }
        } catch (TransactionRolledBackException e) {
            final XAException e2 = new XAException(XAException.XA_RBROLLBACK);
            e2.initCause(e);
            throw e2;
        } catch (InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public void commit(final Xid xid, final boolean onePhase) throws XAException {
        Transaction transaction = getTransaction(xid);
        try {
            final SynchronousListener<Transaction> listener = new SynchronousListener<>();
            transaction.commit(listener);
            listener.awaitUninterruptibly();
            if (false) {
                // todo - detect rollback
                throw new XAException(XAException.XA_RBROLLBACK);
            }
        } catch (TransactionRolledBackException e) {
            final XAException e2 = new XAException(XAException.XA_RBROLLBACK);
            e2.initCause(e);
            throw e2;
        } catch (InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public void rollback(final Xid xid) throws XAException {
        Transaction transaction = getTransaction(xid);
        try {
            final SynchronousListener<Transaction> listener = new SynchronousListener<>();
            transaction.rollback(listener);
            listener.awaitUninterruptibly();
        } catch (TransactionRolledBackException e) {
            return;
        } catch (InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    private Transaction getTransaction(final Xid xid) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        XidKey key = new XidKey(xid);
        Transaction transaction = incompleteTransactions.remove(key);
        if (transaction == null) {
            throw new XAException(XAException.XAER_NOTA);
        }
        return transaction;
    }

    public boolean setTransactionTimeout(final int timeout) throws XAException {
        return false;
    }

    Object writeReplace() {
        return new Serialized(uuid);
    }

    private static final class XidKey {
        private final Xid xid;
        private final int hashCode;

        private XidKey(final Xid xid) {
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

    static class Serialized implements Serializable {

        private static final long serialVersionUID = -4368950567895021414L;

        private final UUID uuid;

        Serialized(final UUID uuid) {
            this.uuid = uuid;
        }

        UUID getUuid() {
            return uuid;
        }

        Object readResolve() throws ObjectStreamException {
            final TransactionXAResource xaResource = XA_RESOURCE_MAP.get(uuid);
            if (xaResource == null) {
                throw new InvalidObjectException("No corresponding XAResource for this serialized instance");
            }
            return xaResource;
        }
    }

    static class Result {
        // in
        final Executor taskExecutor;
        final Problem.Severity maxSeverity;
        // out
        XATransaction transaction;

        Result(final Executor taskExecutor, final Problem.Severity maxSeverity) {
            this.taskExecutor = taskExecutor;
            this.maxSeverity = maxSeverity;
        }
    }
}
