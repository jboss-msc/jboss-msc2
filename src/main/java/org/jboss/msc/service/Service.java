/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package org.jboss.msc.service;

import org.jboss.msc.txn.CommitContext;
import org.jboss.msc.txn.RollbackContext;
import org.jboss.msc.txn.ValidateContext;

/**
 * A service which starts and stops. Services may be stopped and started multiple times.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public interface Service<T> {

    /**
     * Service start lifecycle method, invoked on execution.
     * 
     * @param startContext start context
     */
    void executeStart(StartContext<T> startContext);

    /**
     * Service start validation method.
     * 
     * @param validateContext validate context
     */
    void validateStart(ValidateContext validateContext);

    /**
     * Service start commit method.
     * 
     * @param commitContext commit context
     */
    void commitStart(CommitContext startContext);
    
    /**
     * Service rollback start method.
     * 
     * @param rollbackContext rollback context
     */
    void rollbackStart(RollbackContext rollbackContext);

    
    /**
     * Service stop lifecycle method invoked on execution.
     * 
     * @param stopContext stop context
     */
    void executeStop(StopContext stopContext);
    
    /**
     * Service stop validation method.
     * 
     * @param validateContext validate context
     */
    void validateStop(ValidateContext validateContext);
    
    /**
     * Service stop commit method.
     * 
     * @param commitContext commit context
     */
    void commitStop(CommitContext commitContext);
    
    /**
     * Service rollback stop method.
     * 
     * @param rollbackContext rollback context
     */
    void rollbackStop(RollbackContext rollbackContext);


}
