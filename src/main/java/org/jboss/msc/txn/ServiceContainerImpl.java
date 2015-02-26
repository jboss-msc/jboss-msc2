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

package org.jboss.msc.txn;

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.util.Listener;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.jboss.msc.txn.Helper.setModified;
import static org.jboss.msc.txn.Helper.validateTransaction;

/**
 * A transactional service container.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ServiceContainerImpl implements ServiceContainer {

    private final TransactionController txnController;
    private final Set<ServiceRegistryImpl> registries = Collections.synchronizedSet(new HashSet<ServiceRegistryImpl>());
    private boolean removing, removed;
    private int removedRegistries;
    private NotificationEntry removeObservers;

    ServiceContainerImpl(final TransactionController txnController) {
        this.txnController = txnController;
    }

    public ServiceRegistry newRegistry() {
        synchronized (this) {
            if (removing) {
                throw MSCLogger.SERVICE.cannotCreateRegistryIfContainerWasShutdown();
            }
            final ServiceRegistryImpl returnValue = new ServiceRegistryImpl(this);
            registries.add(returnValue);
            return returnValue;
        }
    }

    TransactionController getTransactionController() {
        return txnController;
    }

    @Override
    public void shutdown(final UpdateTransaction txn) throws IllegalArgumentException, InvalidTransactionStateException {
        shutdown(txn, null);
    }

    @Override
    public void shutdown(final UpdateTransaction txn, final Listener<ServiceContainer> completionListener) throws IllegalArgumentException, InvalidTransactionStateException {
        validateTransaction(txn, txnController);
        setModified(txn);
        while (true) {
            synchronized (this) {
                if (removed) break; // simulated goto for callback listener
                if (completionListener != null) removeObservers = new NotificationEntry(removeObservers, completionListener);
                if (removing) return;
                removing = true;
            }
            synchronized (registries) {
                for (final ServiceRegistryImpl registry : registries) {
                    registry.remove(txn);
                }
            }
            return;
        }
        if (completionListener != null) safeCallListener(completionListener); // open call
    }

    void registryRemoved() {
        NotificationEntry removeObservers;
        synchronized (this) {
            if (++removedRegistries != registries.size()) return;
            removed = true;
            removeObservers = this.removeObservers;
            this.removeObservers = null;
        }
        while (removeObservers != null) {
            safeCallListener(removeObservers.completionListener);
            removeObservers = removeObservers.next;
        }
    }

    void safeCallListener(final Listener<ServiceContainer> listener) {
        try {
            listener.handleEvent(this);
        } catch (final Throwable t) {
            MSCLogger.SERVICE.serviceContainerCompletionListenerFailed(t);
        }
    }

    private static final class NotificationEntry {

        private final NotificationEntry next;
        private final Listener<ServiceContainer> completionListener;

        private NotificationEntry(final NotificationEntry next, final Listener<ServiceContainer> completionListener) {
            this.next = next;
            this.completionListener = completionListener;
        }

    }
}
