package org.jboss.msc.txn;

/**
 * <B>ReadTransaction</B>s don't modify MSC internal states.
 * There can be multiple <B>ReadTransaction</B>s running at the same time.
 * When at least one <B>ReadTransaction</B> is running then there never will be
 * <B>UpdateTransaction</B> associated with the same <B>TransactionController</B>
 * running concurrently.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @see org.jboss.msc.txn.TransactionController
 * @see org.jboss.msc.txn.UpdateTransaction
 */
public interface ReadTransaction extends Transaction {

    /**
     * Register <B>post-commit</B> phase completion listener for notifications
     * @param completionListener to be registered for notifications
     * @throws IllegalStateException if attempting to register new listener for committed transaction
     */
    void addPostCommit(Action completionListener) throws IllegalStateException;

    /**
     * Unregister <B>post-commit</B> phase completion listener from notifications
     * @param completionListener to be unregistered from notifications
     * @throws IllegalStateException if attempting to unregister listener from committed transaction
     */
    void removePostCommit(Action completionListener) throws IllegalStateException;

}
