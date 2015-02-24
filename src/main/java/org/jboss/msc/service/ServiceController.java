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

package org.jboss.msc.service;

import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.UpdateTransaction;
import org.jboss.msc.util.Listener;


/**
 * A controller for a single service instance.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @param <T> service type
 */
public interface ServiceController<T> {

    /**
     * Disables a service, causing this service to stop if it is {@code UP}.
     *
     * @param transaction the transaction
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void disable(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Disables a service, causing this service to stop if it is {@code UP}.
     *
     * @param transaction the transaction
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void disable(UpdateTransaction transaction, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Enables the service, which may start as a result, according to its {@link org.jboss.msc.service.ServiceMode mode} rules.
     * <p> Services are enabled by default.
     *
     * @param transaction the transaction
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void enable(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Enables the service, which may start as a result, according to its {@link org.jboss.msc.service.ServiceMode mode} rules.
     * <p> Services are enabled by default.
     *
     * @param transaction the transaction
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void enable(UpdateTransaction transaction, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Removes this service.<p>
     * All dependent services will be automatically stopped as the result of this operation.
     *
     * @param transaction the transaction
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void remove(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Removes this service.<p>
     * All dependent services will be automatically stopped as the result of this operation.
     *
     * @param transaction the transaction
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void remove(UpdateTransaction transaction, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Restarts this service.
     * 
     * @param transaction the transaction
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void restart(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Restarts this service.
     *
     * @param transaction the transaction
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void restart(UpdateTransaction transaction, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Retries a failed service. Does nothing if the state has not failed.
     *
     * @param transaction the transaction
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void retry(UpdateTransaction transaction) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Retries a failed service. Does nothing if the state has not failed.
     *
     * @param transaction the transaction
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if <code>transaction</code> is null
     * or if transaction controller associated with <code>transaction</code>
     * is not the same as the one associated with this service controller.
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void retry(UpdateTransaction transaction, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Replaces {@code service} by a new service.
     *
     * @param transaction the transaction
     * @param newService the new service to be published
     * @throws java.lang.IllegalArgumentException if any method parameter is <code>null</code>
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void replace(UpdateTransaction transaction, Service<T> newService) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Replaces {@code service} by a new service.
     *
     * @param transaction the transaction
     * @param newService the new service to be published
     * @param completionListener called when operation is finished
     * @throws java.lang.IllegalArgumentException if any method parameter is <code>null</code>
     * @throws org.jboss.msc.txn.InvalidTransactionStateException if transaction is not active.
     */
    void replace(UpdateTransaction transaction, Service<T> newService, Listener<ServiceController<T>> completionListener) throws IllegalArgumentException, InvalidTransactionStateException;

    /**
     * Gets associated service.
     * @return service
     */
    Service<T> getService();

}
