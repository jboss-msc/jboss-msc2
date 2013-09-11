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
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

/**
 * The MSC XA resource implementation.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class TransactionXAResource extends TransactionManagementScheme<XATransaction> implements XAResource {

    private static final Xid[] NO_XIDS = new Xid[0];
    private static final AtomicReferenceFieldUpdater<TransactionXAResource, XATransaction> transactionUpdater = AtomicReferenceFieldUpdater.newUpdater(TransactionXAResource.class, XATransaction.class, "transaction");

    private final TransactionXAResourceManager resourceManager;
    private final Executor taskExecutor;
    private final Problem.Severity maxSeverity;

    @SuppressWarnings("unused")
    private volatile XATransaction transaction;

    TransactionXAResource(final TransactionXAResourceManager resourceManager, final Executor taskExecutor, final Problem.Severity maxSeverity) {
        this.resourceManager = resourceManager;
        this.taskExecutor = taskExecutor;
        this.maxSeverity = maxSeverity;
    }

    public boolean isSameRM(final XAResource resource) throws XAException {
        return resource instanceof TransactionXAResource && isSameRM((TransactionXAResource) resource);
    }

    public boolean isSameRM(final TransactionXAResource resource) throws XAException {
        return resource != null && resourceManager == resource.resourceManager;
    }

    public Xid[] recover(final int flags) throws XAException {
        switch (flags) {
            case TMNOFLAGS:
            case TMENDRSCAN: return NO_XIDS;
            case TMSTARTRSCAN: break;
            default: throw new XAException(XAException.XAER_INVAL);
        }
        return resourceManager.recover();
    }

    public void start(final Xid xid, final int flags) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        if (flags == TMRESUME) {
            // resume suspended
            final XATransaction xaTransaction = resourceManager.getTransaction(xid);
            if (xaTransaction == null) {
                throw new XAException(XAException.XAER_NOTA);
            }
            if (! transactionUpdater.compareAndSet(this, null, xaTransaction)) {
                throw new XAException(XAException.XAER_OUTSIDE);
            }
            // association complete!
        } else if (flags == TMJOIN) {
            // join with existing branch
            final XATransaction xaTransaction = resourceManager.getTransaction(xid);
            if (xaTransaction == null) {
                throw new XAException(XAException.XAER_NOTA);
            }
            if (! transactionUpdater.compareAndSet(this, null, xaTransaction)) {
                throw new XAException(XAException.XAER_OUTSIDE);
            }
            // association complete!
        } else if (flags == TMNOFLAGS) {
            final Executor taskExecutor = this.taskExecutor;
            final Problem.Severity maxSeverity = this.maxSeverity;
            if (taskExecutor == null || maxSeverity == null) {
                // recovery-only XAR
                throw new XAException(XAException.XAER_INVAL);
            }
            // create new branch
            XATransaction transaction = this.transaction;
            if (transaction != null) {
                throw new XAException(XAException.XAER_OUTSIDE);
            }
            transaction = registerTransaction(new XATransaction(resourceManager.getController(), taskExecutor, maxSeverity));
            boolean ok = false;
            try {
                if (! transactionUpdater.compareAndSet(this, null, transaction)) {
                    throw new XAException(XAException.XAER_OUTSIDE);
                }
                if (! resourceManager.registerTransaction(xid, transaction)) {
                    // ignore result
                    transactionUpdater.compareAndSet(this, transaction, null);
                    throw new XAException(XAException.XAER_DUPID);
                }
                ok = true;
                // association complete!
            } finally {
                if (! ok) {
                    transaction.destroy();
                }
            }
        } else {
            throw new XAException(XAException.XAER_INVAL);
        }
    }

    XATransaction registerTransaction(final XATransaction transaction) {
        try {
            Transactions.register(transaction);
        } catch (final IllegalStateException e) {
            transaction.forceStateRolledBack();
            throw e;
        }
        return transaction;
    }

    public void end(final Xid xid, final int flags) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        if (flags != TMSUCCESS && flags != TMSUSPEND && flags != TMFAIL) {
            throw new XAException(XAException.XAER_INVAL);
        }
        XATransaction transaction;
        do {
            transaction = this.transaction;
            if (transaction == null) {
                throw new XAException(XAException.XAER_NOTA);
            }
            final XATransaction resourceManagerTransaction = resourceManager.getTransaction(xid);
            if (resourceManagerTransaction == null) {
                throw new XAException(XAException.XAER_NOTA);
            }
            if (resourceManagerTransaction != transaction) {
                throw new XAException(XAException.XAER_RMERR);
            }
        } while (! transactionUpdater.compareAndSet(this, transaction, null));
        if (flags == TMFAIL) {
            // also roll back the txn and erase our association with it
            resourceManager.unregisterTransaction(xid, transaction);
            try {
                final SynchronousListener<AbortResult<? extends Transaction>> listener = new SynchronousListener<>();
                transaction.abort(listener);
                listener.awaitUninterruptibly();
            } catch (final InvalidTransactionStateException e) {
                final XAException e2 = new XAException(XAException.XAER_PROTO);
                e2.initCause(e);
                throw e2;
            }
        }
    }

    public int prepare(final Xid xid) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        // this method deals directly with the resource manager.
        final XATransaction transaction = resourceManager.getTransaction(xid);
        if (transaction == null) {
            // transaction is gone
            throw new XAException(XAException.XAER_NOTA);
        }
        try {
            final SynchronousListener<PrepareResult<? extends Transaction>> listener = new SynchronousListener<>();
            transaction.prepare(listener);
            final PrepareResult<? extends Transaction> prepareResult = listener.awaitUninterruptibly();
            if (prepareResult.isPrepared()) {
                // todo - a way to establish whether changes were made?
                return XA_OK;
            } else {
                throw new XAException(XAException.XA_RBROLLBACK);
            }
        } catch (final InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public void forget(final Xid xid) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        // this method deals directly with the resource manager.
        final XATransaction transaction = resourceManager.removeTransaction(xid);
        if (transaction == null) {
            // transaction is gone
            return;
        }
        try {
            final SynchronousListener<RollbackResult<? extends Transaction>> listener = new SynchronousListener<>();
            transaction.rollback(listener);
            try {
                listener.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                // we want the TM to try again later please
                throw new XAException(XAException.XAER_RMERR);
            }
        } catch (final InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public void commit(final Xid xid, final boolean onePhase) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        final Transaction transaction = resourceManager.getTransaction(xid);
        if (transaction == null) {
            throw new XAException(XAException.XAER_NOTA);
        }
        try {
            final SynchronousListener<CommitResult<? extends Transaction>> listener = new SynchronousListener<>();
            transaction.commit(listener);
            listener.awaitUninterruptibly();
        } catch (final InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public void rollback(final Xid xid) throws XAException {
        if (xid == null) {
            throw new XAException(XAException.XAER_INVAL);
        }
        final Transaction transaction = resourceManager.getTransaction(xid);
        if (transaction == null) {
            throw new XAException(XAException.XAER_NOTA);
        }
        try {
            final SynchronousListener<AbortResult<? extends Transaction>> listener = new SynchronousListener<>();
            transaction.abort(listener);
            listener.awaitUninterruptibly();
        } catch (final InvalidTransactionStateException e) {
            final XAException e2 = new XAException(XAException.XAER_PROTO);
            e2.initCause(e);
            throw e2;
        }
    }

    public int getTransactionTimeout() throws XAException {
        return Integer.MAX_VALUE;
    }

    public boolean setTransactionTimeout(final int timeout) throws XAException {
        return false;
    }

    Object writeReplace() {
        return new Serialized(resourceManager.getUuid());
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
            final TransactionXAResource xaResource = TransactionXAResourceManager.getSerialized(uuid);
            if (xaResource == null) {
                throw new InvalidObjectException("No corresponding XAResource for this serialized instance");
            }
            return xaResource;
        }
    }
}
