/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.msc.service.ServiceName;

/**
 * Dependent service.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
interface Dependent {

    /**
     * Returns dependent service name.
     */
    ServiceName getServiceName();

    /**
     * Notifies that a dependency is satisfied (during installation, all dependencies are
     * considered unsatisfied until a dependencySatisfied notification is received).
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     * @return the transition task resulting of this notification, if any
     */
    TaskController<?> dependencySatisfied(Transaction transaction, TaskFactory taskFactory);

    /**
     * Notifies that a dependency no longer satisfied.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     * @return the transition task resulting of this notification, if any
     */
    TaskController<?> dependencyUnsatisfied(Transaction transaction, TaskFactory taskFactory);

}