package org.jboss.msc.txn;

/**
 * <B>UpdateTransaction</B>s modify MSC internal states.
 * There can be only single <B>UpdateTransaction</B> running at the same time.
 * This restriction applies only to transactions created by the same <B>TransactionController</B>.
 * When <B>UpdateTransaction</B> is running there never will be
 * <B>ReadTransaction</B> associated with the same <B>TransactionController</B>
 * running concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @see org.jboss.msc.txn.ReadTransaction
 * @see org.jboss.msc.txn.TransactionController
 */
public interface UpdateTransaction extends ReadTransaction {

    /**
     * Indicates whether this transaction have been prepared.
     * @return {@code true} if already prepared, {@code false} otherwise
     */
    boolean isPrepared();

}
