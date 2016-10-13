/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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
