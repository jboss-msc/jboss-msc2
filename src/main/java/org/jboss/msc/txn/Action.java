/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
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

import org.jboss.msc.util.Listener;

/**
 * The <code>Action</code> interface provides an access to the transaction's
 * <code>Post Phase</code> lifecycle events. There are three
 * <code>Post Phase</code> events defined for transaction:
 * <UL>
 *     <LI><code>post-prepare</code> - called after all tasks have been executed but before transaction will enter <B>PREPARED</B> state</LI>
 *     <LI><code>post-restart</code> - called after prepared transaction restart request but before transaction will enter <B>RESTARTED</B> state</LI>
 *     <LI><code>post-commit</code> - called after prepared transaction commit request but before transaction will enter <B>COMMITTED</B> state</LI>
 * </UL>
 * <P>
 * Action processing code communicates with associated transaction
 * via {@link org.jboss.msc.txn.ActionContext#complete()} method.
 * Transaction will block until all actions will not be completed.
 * This allows 'Atomicity' like behaviour for compound operations
 * leveraging MSC2 transactions.
 * </P>
 * <P>Action processing code cannot fail. Users have to be careful
 * to always call {@link org.jboss.msc.txn.ActionContext#complete()}
 * otherwise transaction will block. That would result in deadlock-like
 * behaviour in the running system.
 * </P>
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface Action extends Listener<ActionContext> {

    void handleEvent(ActionContext ctx);

}
