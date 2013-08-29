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
 * Transaction's rollback phase result.
 * <br/><br/>
 * The result of transaction's rollback request can be only
 * rolled back transaction.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface RollbackResult<T extends Transaction> extends Result<T> {
    /**
     * Returns <code>true</code> if transaction have been rolled back, <code>false</code> otherwise.
     * @return <code>true</code> if transaction have been rolled back, <code>false</code> otherwise.
     */
    boolean isRolledBack(); // TODO: It always returns true, so do we really need this method?
}
