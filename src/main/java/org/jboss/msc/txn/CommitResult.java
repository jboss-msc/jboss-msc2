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

/**
 * Transaction's commit phase result.
 * <br/><br/>
 * The result of transaction's commit request can be either
 * committed or aborted transaction.
 * There are two possible transaction flows from commit point of view:
 * <ul>
 * <li>
 * The transaction was prepared and commit request have been sent.
 * The transaction will be committed as a result.
 * </li>
 * <li>
 * If both commit() and abort() have been requested during
 * transaction's prepare phase execution, the commit will not be executed at all
 * because abort request has precedence in such case.
 * The transaction will be aborted as a result.
 * </li>
 * </ul>
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * 
 * @param <T> transaction
 */
public interface CommitResult<T extends Transaction> extends Result<T> {
    /**
     * Returns <code>true</code> if transaction have been committed, <code>false</code> if it have been aborted.
     * @return <code>true</code> if transaction have been committed, <code>false</code> if it have been aborted.
     */
    boolean isCommitted();
}
