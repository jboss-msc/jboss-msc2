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

    /**
     * Register <B>post-prepare</B> phase completion listener for notifications
     * @param completionListener to be registered for notifications
     * @throws IllegalStateException if attempting to register new listener for non active transaction
     */
    void addPostPrepare(Action completionListener) throws IllegalStateException;

    /**
     * Unregister <B>post-prepare</B> phase completion listener from notifications
     * @param completionListener to be unregistered from notifications
     * @throws IllegalStateException if attempting to unregister listener from non active transaction
     */
    void removePostPrepare(Action completionListener) throws IllegalStateException;

    /**
     * Register <B>post-restart</B> phase completion listener for notifications
     * @param completionListener to be registered for notifications
     * @throws IllegalStateException if attempting to register new listener for committed or restarted transaction
     */
    void addPostRestart(Action completionListener) throws IllegalStateException;

    /**
     * Unregister <B>post-restart</B> phase completion listener from notifications
     * @param completionListener to be unregistered from notifications
     * @throws IllegalStateException if attempting to unregister listener from committed or restarted transaction
     */
    void removePostRestart(Action completionListener) throws IllegalStateException;

}
