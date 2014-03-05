package org.jboss.msc.txn;

import org.jboss.msc.service.ServiceRegistry;

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
        if (txnController != txn.txnController) {
            throw TXN.transactionControllerMismatch();
        }
        txn.ensureIsActive();
    }

    static void validateRegistry(final ServiceRegistry registry) {
        if (registry == null) {
            throw TXN.methodParameterIsNull("registry");
        }
        if (!(registry instanceof ServiceRegistryImpl)) {
            throw TXN.methodParameterIsInvalid("registry");
        }
    }

}
