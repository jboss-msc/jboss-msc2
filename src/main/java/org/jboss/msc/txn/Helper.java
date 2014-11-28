package org.jboss.msc.txn;

import org.jboss.msc.service.ServiceRegistry;

import java.security.AccessController;

import static org.jboss.msc._private.MSCLogger.TXN;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class Helper {

    private Helper() {}

    static void validateTransaction(final Transaction txn, final TransactionController txnController)
        throws IllegalArgumentException, InvalidTransactionStateException {
        if (txn == null) {
            throw TXN.methodParameterIsNull("txn");
        }
        final AbstractTransaction abstractTxn = getAbstractTransaction(txn);
        if (txnController != abstractTxn.txnController) {
            throw TXN.transactionControllerMismatch();
        }
        abstractTxn.ensureIsActive();
    }

    static AbstractTransaction getAbstractTransaction(final Transaction transaction) throws IllegalArgumentException {
        if (transaction instanceof BasicUpdateTransaction) return ((BasicUpdateTransaction)transaction).getDelegate();
        if (transaction instanceof BasicReadTransaction) return (BasicReadTransaction)transaction;
        throw TXN.illegalTransaction();
    }

    static void validateRegistry(final ServiceRegistry registry) {
        if (registry == null) {
            throw TXN.methodParameterIsNull("registry");
        }
        if (!(registry instanceof ServiceRegistryImpl)) {
            throw TXN.methodParameterIsInvalid("registry");
        }
    }

    static void setModified(final UpdateTransaction transaction) {
        ((BasicUpdateTransaction)transaction).setModified();
    }

    static ClassLoader setTCCL(final ClassLoader newTCCL) {
        final SecurityManager sm = System.getSecurityManager();
        final SetTCCLAction setTCCLAction = new SetTCCLAction(newTCCL);
        if (sm != null) {
            return AccessController.doPrivileged(setTCCLAction);
        } else {
            return setTCCLAction.run();
        }
    }

}
