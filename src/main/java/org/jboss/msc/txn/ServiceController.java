/*
 * JBoss, Home of Professional Open Source.
 * * Copyright 2012 Red Hat, Inc., and individual contributors
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
 * A controller for a single service instance.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface ServiceController {

    /**
     * Removes this service.<p>
     * All dependent services will be automatically stopped as the result of this operation.
     * 
     * @param transaction the transaction
     */
    public void remove(Transaction transaction);

    /**
     * Restarts this service.
     * 
     * @param transaction the transaction
     */
    public void restart(Transaction transaction);

    /**
     * Retries a failed service. Does nothing if the state has not failed.
     *
     * @param transaction the transaction
     */
    public void retry(Transaction transaction);
}
