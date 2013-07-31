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
package org.jboss.msc.test.services;

import static org.jboss.msc.service.DependencyFlag.UNREQUIRED;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.jboss.msc.service.ManagementContext;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.test.utils.AbstractServiceTest;
import org.jboss.msc.test.utils.CompletionListener;
import org.jboss.msc.test.utils.TestService;
import org.jboss.msc.test.utils.TestService.DependencyInfo;
import org.jboss.msc.txn.Transaction;
import org.jboss.msc.txn.TransactionController;
import org.junit.Before;
import org.junit.Test;

/**
 * ManagementContext test case.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class ManagementContextTestCase extends AbstractServiceTest {
    private static final ServiceName serviceAName = ServiceName.of("a", "different", "service", "name");
    private static final ServiceName serviceBName = ServiceName.of("u", "n", "e", "x", "p", "e", "c", "ted");
    private static final ServiceName serviceCName = ServiceName.of("just", "C");
    private static final ServiceName serviceDName = ServiceName.of("D");
    private static final ServiceName serviceEName = ServiceName.of("e", "service");
    private static final ServiceName serviceFName = ServiceName.of("f");
    private static final ServiceName serviceGName = ServiceName.of("g");
    private static final ServiceName serviceHName = ServiceName.of("H");
    private static final ServiceName serviceIName = ServiceName.of("iresource", "service");
    private ManagementContext managementContext;
    private ServiceRegistry registry1;
    private TestService serviceA;
    private TestService serviceB;
    private TestService serviceC;
    private TestService serviceD;
    private ServiceRegistry registry2;
    private TestService serviceE;
    private TestService serviceF;
    private TestService serviceG;
    private TestService serviceH;
    private ServiceRegistry registry3;

    @Before
    public void setup() throws InterruptedException {
        // registry1: contains A, B, and C
        registry1 = serviceContainer.newRegistry();
        serviceA = addService(registry1, serviceAName);
        serviceB = addService(registry1, serviceBName);
        serviceC = addService(registry1, serviceCName);
        // registry 2, contains D, E->D, F->(D and B), G->C, and H -> G services
        registry2 = serviceContainer.newRegistry();
        serviceD = addService(registry2, serviceDName);
        serviceE = addService(registry2, serviceEName, new DependencyInfo<TestService>(serviceDName, UNREQUIRED));
        serviceF = addService(registry2, serviceFName, false, ServiceMode.ACTIVE, new DependencyInfo<TestService>(serviceDName, UNREQUIRED), new DependencyInfo<TestService>(serviceBName, registry1, UNREQUIRED));
        serviceG = addService(registry2, serviceGName, false, ServiceMode.ACTIVE, new DependencyInfo<TestService>(serviceCName, registry1, UNREQUIRED));
        serviceH = addService(registry2, serviceHName, false, ServiceMode.ACTIVE, new DependencyInfo<TestService>(serviceGName, UNREQUIRED));
        // registry 3, empty
        registry3 = serviceContainer.newRegistry();
        // all services are up
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());

        this.managementContext = txnController.getManagementContext();
        assertNotNull(managementContext);
        // invoke a few times, all of them should be valid
        assertNotNull(txnController.getManagementContext());
        assertNotNull(txnController.getManagementContext());
        assertNotNull(txnController.getManagementContext());
        assertNotNull(txnController.getManagementContext());
    }

    @Test
    public void disableServiceA() throws InterruptedException {
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.disableService(registry1, serviceAName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertFalse(serviceA.isUp());
        // all other services remain UP:
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void disableServiceB() throws InterruptedException {
        Transaction transaction = newTransaction();
        CompletionListener listener = new CompletionListener();
        try {
            managementContext.disableService(registry1, serviceBName, transaction);
            // idempotent
            managementContext.disableService(registry1, serviceBName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertFalse(serviceB.isUp());
        // service F depends on B
        assertFalse(serviceF.isUp());
        assertTrue(serviceA.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());

        // idempotent
        transaction = newTransaction();
        listener = new CompletionListener();
        try {
            managementContext.disableService(registry1, serviceBName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertFalse(serviceB.isUp());
        // service F depends on B
        assertFalse(serviceF.isUp());
        assertTrue(serviceA.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void enableServiceC() throws InterruptedException {
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.enableService(registry1, serviceCName, transaction);
            managementContext.disableService(registry1, serviceCName, transaction);
            managementContext.enableService(registry1, serviceCName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void enableServiceD() throws InterruptedException {
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.disableService(registry2, serviceDName, transaction);
            managementContext.enableService(registry2, serviceDName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void enableServiceA() throws InterruptedException {
        disableServiceA();
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.enableService(registry1, serviceAName, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void disableRegistry1() throws InterruptedException {
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.disableRegistry(registry1, transaction);
            
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertFalse(serviceA.isUp());
        assertFalse(serviceB.isUp());
        assertFalse(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertFalse(serviceF.isUp());
        assertFalse(serviceG.isUp());
        assertFalse(serviceH.isUp());
    }

    @Test
    public void disableRegistry2() throws InterruptedException {
        Transaction transaction = newTransaction();
        CompletionListener listener = new CompletionListener();
        try {
            managementContext.disableRegistry(registry2, transaction);
            // idempotent
            managementContext.disableRegistry(registry2, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertFalse(serviceD.isUp());
        assertFalse(serviceE.isUp());
        assertFalse(serviceF.isUp());
        assertFalse(serviceG.isUp());
        assertFalse(serviceH.isUp());

        // idempotent
        transaction = newTransaction();
        listener = new CompletionListener();
        try {
            managementContext.disableRegistry(registry2, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertFalse(serviceD.isUp());
        assertFalse(serviceE.isUp());
        assertFalse(serviceF.isUp());
        assertFalse(serviceG.isUp());
        assertFalse(serviceH.isUp());
    }

    @Test
    public void enableRegistry3() throws InterruptedException {
        Transaction transaction = newTransaction();
        CompletionListener listener = new CompletionListener();
        try {
            managementContext.enableRegistry(registry3, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        // nothing happens, registry 3 is empty
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());

        transaction = newTransaction();
        listener = new CompletionListener();
        try {
            managementContext.disableRegistry(registry3, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        // nothing happens, registry 3 is empty
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());

        // oops, no longer empty
        TestService serviceI = addService(registry3,  serviceIName);
        assertFalse(serviceI.isUp()); // as registry 3 is disabled, serviceI won't start

        transaction = newTransaction();
        listener = new CompletionListener();
        try {
            managementContext.enableRegistry(registry3, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        // service I finally starts
        assertTrue(serviceI.isUp());
        // remainder services keep the same
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void enableRegistry1() throws InterruptedException {
        final Transaction transaction = newTransaction();
        final CompletionListener listener = new CompletionListener();
        try {
            managementContext.enableRegistry(registry1, transaction);
            managementContext.disableRegistry(registry1, transaction);
            managementContext.enableRegistry(registry1, transaction);
        } finally {
            txnController.commit(transaction, listener);
            listener.awaitCompletion();
        }
        assertTrue(serviceA.isUp());
        assertTrue(serviceB.isUp());
        assertTrue(serviceC.isUp());
        assertTrue(serviceD.isUp());
        assertTrue(serviceE.isUp());
        assertTrue(serviceF.isUp());
        assertTrue(serviceG.isUp());
        assertTrue(serviceH.isUp());
    }

    @Test
    public void outsiderTransaction() {
        final TransactionController outsiderController = TransactionController.createInstance();
        final Transaction outsiderTransaction = outsiderController.create(defaultExecutor);

        IllegalArgumentException expected = null;
        try {
            managementContext.disableRegistry(serviceRegistry, outsiderTransaction);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            managementContext.enableRegistry(serviceRegistry, outsiderTransaction);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            managementContext.enableService(serviceRegistry, serviceAName, outsiderTransaction);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            managementContext.disableService(serviceRegistry, serviceAName, outsiderTransaction);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            managementContext.shutdownContainer(serviceContainer, outsiderTransaction);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        outsiderController.commit(outsiderTransaction, null);
    }

    @Test
    public void outsiderService() {
        final Transaction transaction = newTransaction();
        ServiceNotFoundException expected = null;
        try {
            managementContext.enableService(registry3, serviceAName, transaction);
        } catch (ServiceNotFoundException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            managementContext.disableService(registry3, serviceAName, transaction);
        } catch (ServiceNotFoundException e) {
            expected = e;
        }
        assertNotNull(expected);

        txnController.commit(transaction, null);
    }
}
