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
 * Transaction's prepare phase result.
 * <br/><br/>
 * The result of transaction's prepare request can be either
 * prepared or aborted transaction.
 * There are two possible transaction flows from prepare point of view:
 * <ul>
 * <li>
 * Transaction is prepared and abort have not been requested.
 * The transaction will be prepared as a result.
 * </li>
 * <li>
 * If transaction's prepare is in progress and abort have been requested
 * the abort request will take over execution flow.
 * The transaction will be aborted as a result.
 * </li>
 * </ul>
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface PrepareResult<T extends Transaction> extends Result<T> {
    /**
     * Returns <code>true</code> if transaction have been prepared, <code>false</code> if it have been aborted.
     * @return <code>true</code> if transaction have been prepared, <code>false</code> if it have been aborted.
     */
    boolean isPrepared();
}
