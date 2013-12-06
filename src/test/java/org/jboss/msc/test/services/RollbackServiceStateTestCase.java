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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.test.utils.AbstractServiceTest;
import org.jboss.msc.test.utils.TestService;
import org.jboss.msc.test.utils.TestService.DependencyInfo;
import org.jboss.msc.test.utils.TestServiceBuilder;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.ServiceController;
import org.junit.Test;

/**
 * Tests for rolling back transactions that affect the state of one or more services.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
public class RollbackServiceStateTestCase extends AbstractServiceTest {

    private static final ServiceName firstSN = ServiceName.of("first");
    private static final ServiceName secondSN = ServiceName.of("second");

    /**
     * Usecase:
     * <UL>
     * <LI>add a service and rollback the transaction</LI>
     * </UL>
     */
    @Test
    public void revertServiceInstallation() {
        // new transaction
        final BasicTransaction txn = newTransaction();
        // create service builder
        final ServiceContext serviceContext = txnController.getServiceContext();
        assertServiceContext(serviceContext);
        final ServiceBuilder<Void> serviceBuilder = serviceContext.addService(serviceRegistry, firstSN, txn);
        // create test service
        final TestService firstService = new TestService(firstSN, serviceBuilder, false);
        serviceBuilder.setService(firstService);
        // install
        final ServiceController serviceController = serviceBuilder.install();
        assertNotNull(serviceController);
        firstService.waitStart();
        assertTrue(firstService.isUp());
        // rollback
        rollback(txn);
        // check service is down
        assertFalse(firstService.isUp());
        // check service is uninstalled
        assertNull(serviceRegistry.getService(firstSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add a service and its required dependency as part of same transaction</LI>
     * <LI>remove dependency: the transaction will automatically rollback</LI>
     * </UL>
     */
    @Test
    public void revertServiceRemoval() {
        final TestService firstService, secondService;
        final ServiceController secondServiceController;
        // new transaction
        final BasicTransaction txn = newTransaction();
        try {
            // create service builder
            final TestServiceBuilder firstServiceBuilder = new TestServiceBuilder(txn, firstSN, new DependencyInfo<Void>(
                    secondSN, DependencyFlag.REQUIRED));
            firstService = firstServiceBuilder.getService();
            final ServiceController firstServiceController = firstServiceBuilder.install();
            assertNotNull(firstServiceController);
            // install missing required dependency
            final TestServiceBuilder secondServiceBuilder = new TestServiceBuilder(txn, secondSN);
            secondService = secondServiceBuilder.getService();
            secondServiceController = secondServiceBuilder.install();
            assertNotNull(secondServiceController);
            secondService.waitStart();
            firstService.waitStart();
            assertTrue(secondService.isUp());
            assertTrue(firstService.isUp());
        } finally {
            prepare(txn);
            commit(txn);
        }

        // atempt to remove second service, transaction will rollback
        final BasicTransaction txn2 = newTransaction();
        try {
            secondServiceController.remove(txn2);
            secondService.waitStop();
        } finally {
            assertFalse(attemptToCommit(txn2));
        }

        // check both services are up
        assertTrue(secondService.isUp());
        assertTrue(firstService.isUp());
        serviceRegistry.getRequiredService(firstSN);
        serviceRegistry.getRequiredService(secondSN);
    }

    @Test
    public void revertServiceRemoval2() {
        final TestService firstService, secondService;
        final ServiceController secondServiceController;

        // new transaction
        final BasicTransaction txn = newTransaction();
        try {
            // create service builder
            final TestServiceBuilder firstServiceBuilder = new TestServiceBuilder(txn, firstSN, new DependencyInfo<Void>(secondSN, DependencyFlag.UNREQUIRED));
            firstService = firstServiceBuilder.getService();
            final ServiceController firstServiceController = firstServiceBuilder.install();
            assertNotNull(firstServiceController);
            // install missing unrequired dependency
            final TestServiceBuilder secondServiceBuilder = new TestServiceBuilder(txn, secondSN);
            secondService = secondServiceBuilder.getService();
            secondServiceController = secondServiceBuilder.install();
            assertNotNull(secondServiceController);
            secondService.waitStart();
            firstService.waitStart();
            assertTrue(secondService.isUp());
            assertTrue(firstService.isUp());
        } finally {
            prepare(txn);
            commit(txn);
        }

        // atempt to remove second service, transaction will rollback
        final BasicTransaction txn2 = newTransaction();
        try {
            secondServiceController.remove(txn2);
            secondService.waitStop();
        } finally {
            rollback(txn2);
        }

        final BasicTransaction txn3 = newTransaction();
        try {
            secondServiceController.disable(txn3);
        } finally {
            prepare(txn3);
            commit(txn3);
        }

        final BasicTransaction txn4 = newTransaction();
        try {
            secondServiceController.enable(txn4);
        } finally {
            prepare(txn4);
            commit(txn4);
        }

        // check both services are up
        assertTrue(secondService.isUp());
        assertTrue(firstService.isUp());
        serviceRegistry.getRequiredService(firstSN);
        serviceRegistry.getRequiredService(secondSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add first service, ON_DEMAND</LI>
     * <LI>add second service, that depends on first services, rollback after first service has started</LI>
     * </UL>
     */
    @Test
    public void revertServiceStart() {
        final TestService firstService = addService(firstSN, ServiceMode.ON_DEMAND);
        assertFalse(firstService.isUp());
        // add a dependent and rollback
        final BasicTransaction txn = newTransaction();
        final TestService secondService;
        try {
            // create service builder
            final ServiceContext serviceContext = txnController.getServiceContext();
            assertServiceContext(serviceContext);
            final ServiceBuilder<Void> serviceBuilder = serviceContext.addService(serviceRegistry, secondSN, txn);
            secondService = new TestService(secondSN, serviceBuilder, false);
            serviceBuilder.setService(secondService);
            serviceBuilder.addDependency(firstSN);
            serviceBuilder.install();
            firstService.waitStart();
            secondService.waitStart();
            assertTrue(secondService.isUp());
            assertTrue(firstService.isUp());
        } finally {
            rollback(txn);
        }

        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add first service, ON_DEMAND, and second service, that depends on first service</LI>
     * <LI>remove second service and roll it back</LI>
     * </UL>
     */
    @Test
    public void revertServiceStopAndRemoval() {
        final BasicTransaction txn1 = newTransaction();
        final TestServiceBuilder firstServiceBuilder = new TestServiceBuilder(txn1, firstSN, ServiceMode.ON_DEMAND);
        final TestServiceBuilder secondServiceBuilder = new TestServiceBuilder(txn1, secondSN, firstSN);
        firstServiceBuilder.install();
        final ServiceController secondController = secondServiceBuilder.install();
        final TestService firstService = firstServiceBuilder.getService();
        final TestService secondService = firstServiceBuilder.getService();
        prepare(txn1);
        commit(txn1);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        // remove second service and rollback
        final BasicTransaction txn2 = newTransaction();
        try {
            secondController.remove(txn2);
            firstService.waitStop();
        } finally {
            rollback(txn2);
        }
        assertTrue(secondService.isUp());
        assertTrue(firstService.isUp());
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add a service</LI>
     * <LI>disable service and rollback the transaction</LI>
     * </UL>
     */
    @Test
    public void revertServiceDisable() {
        final TestService firstService = addService(firstSN);
        assertTrue(firstService.isUp());
        // disable service and rollback
        final BasicTransaction txn = newTransaction();
        serviceRegistry.getRequiredService(firstSN).disable(txn);
        firstService.waitStop();
        assertFalse(firstService.isUp());
        rollback(txn);
        // service should be down
        assertTrue(firstService.isUp());
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add a service</LI>
     * <LI>disable and enable service, then rollback the transaction</LI>
     * </UL>
     */
    @Test
    public void revertServiceDisableEnable() {
        final TestService firstService = addService(firstSN);
        assertTrue(firstService.isUp());
        // disable and enable service, then rollback
        final BasicTransaction txn = newTransaction();
        final ServiceController controller = serviceRegistry.getRequiredService(firstSN);
        controller.disable(txn);
        firstService.waitStop();
        controller.enable(txn);
        rollback(txn);
        // service should be up
        assertTrue(firstService.isUp());
    }

    /**
     * Usecase:
     * <UL>
     * <LI>add a service</LI>
     * <LI>disable service</LI>
     * <LI>enable service and rollback the transaction</LI>
     * </UL>
     */
    @Test
    public void revertServiceEnable() {
        final TestService firstService = addService(firstSN);
        assertTrue(firstService.isUp());
        // disable service
        final BasicTransaction txn1 = newTransaction();
        final ServiceController controller = serviceRegistry.getRequiredService(firstSN);
        controller.disable(txn1);
        prepare(txn1);
        commit(txn1);
        // enable service and rollback
        final BasicTransaction txn2 = newTransaction();
        controller.enable(txn2);
        rollback(txn2);
        // check service is down
        assertFalse(firstService.isUp());
    }

}
