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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.test.utils.AbstractServiceTest;
import org.jboss.msc.test.utils.DependencyInfo;
import org.jboss.msc.test.utils.SimpleTestService;
import org.jboss.msc.txn.BasicTransaction;
import org.junit.Test;

/**
 * Test for {@code SimpleService}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class SimpleServiceTestCase extends AbstractServiceTest {

    private static final ServiceName firstSN = ServiceName.of("first", "simple", "service");
    private static final ServiceName secondSN = ServiceName.of("second");

    @Test
    public void installAndRemoveSimpleService() {
        final BasicTransaction txn1 = newTransaction();
        final SimpleTestService simpleService;
        final ServiceController firstServiceController;
        try {
            ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn1);
            simpleService = new SimpleTestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(simpleService);
            firstServiceController = serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(simpleService.isUp());
        assertNotNull(firstServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));

        final BasicTransaction txn2 = newTransaction();
        try {
            firstServiceController.remove(txn2);
        } finally {
            prepare(txn2);
            commit(txn2);
        }
        assertFalse(simpleService.isUp());
        assertNull(serviceRegistry.getService(firstSN));
    }

    @Test
    public void installAndRemoveSimpleServiceDependent() {
        final BasicTransaction txn1 = newTransaction();
        final SimpleTestService firstService;
        final SimpleTestService secondService;
        final ServiceController firstServiceController;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, secondSN, txn1);
            secondService = new SimpleTestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceController = secondServiceBuilder.install();

            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn1);
            firstService = new SimpleTestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertNotNull(firstServiceController);
        assertNotNull(secondServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn2 = newTransaction();
        try {
            secondServiceController.remove(txn2);
            firstServiceController.remove(txn2);
        } finally {
            prepare(txn2);
            commit(txn2);
        }
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(firstSN));
        assertNull(serviceRegistry.getService(secondSN));
    }

    @Test
    public void installAndRemoveSimpleServiceDependentMultipleTxns() {
        final BasicTransaction txn1 = newTransaction();
        final SimpleTestService secondService;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, secondSN, txn1);
            secondService = new SimpleTestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceController = secondServiceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(secondService.isUp());
        assertNotNull(secondServiceController);
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn2 = newTransaction();
        final SimpleTestService firstService;
        final ServiceController firstServiceController;
        try {
            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn2);
            firstService = new SimpleTestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
        } finally {
            prepare(txn2);
            commit(txn2);
        }
        assertTrue(firstService.isUp());
        assertNotNull(firstServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));

        final BasicTransaction txn3 = newTransaction();
        try {
            firstServiceController.remove(txn3);
        } finally {
            prepare(txn3);
            commit(txn3);
        }
        assertFalse(firstService.isUp());
        assertNull(serviceRegistry.getService(firstSN));

        assertTrue(secondService.isUp());
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));
        final BasicTransaction txn4 = newTransaction();
        try {
            secondServiceController.remove(txn4);
        } finally {
            prepare(txn4);
            commit(txn4);
        }
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    @Test
    public void abortSimpleServiceDependent() {
        final BasicTransaction txn1 = newTransaction();
        final SimpleTestService secondService;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, secondSN, txn1);
            secondService = new SimpleTestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceBuilder.setMode(ServiceMode.ON_DEMAND);
            secondServiceController = secondServiceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertFalse(secondService.isUp());
        assertNotNull(secondServiceController);
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn2 = newTransaction();
        final SimpleTestService firstService;
        final ServiceController firstServiceController;
        try {
            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn2);
            firstService = new SimpleTestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
            prepare(txn2);
            firstService.waitStart();
        } finally {
            abort(txn2);
        }
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNotNull(firstServiceController);
        assertNull(serviceRegistry.getService(firstSN));

        final BasicTransaction txn3 = newTransaction();
        try {
            secondServiceController.remove(txn3);
        } finally {
            prepare(txn3);
            abort(txn3);
        }
        assertFalse(secondService.isUp());
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn4 = newTransaction();
        try {
            secondServiceController.remove(txn4);
        } finally {
            prepare(txn4);
            commit(txn4);
        }
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    @Test
    public void rollbackSimpleServiceDependent() {
        final BasicTransaction txn1 = newTransaction();
        final SimpleTestService secondService;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, secondSN, txn1);
            secondService = new SimpleTestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceBuilder.setMode(ServiceMode.LAZY);
            secondServiceController = secondServiceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertFalse(secondService.isUp());
        assertNotNull(secondServiceController);
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn2 = newTransaction();
        SimpleTestService firstService;
        ServiceController firstServiceController;
        try {
            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn2);
            firstService = new SimpleTestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
            firstService.waitStart();
            secondService.waitStart();
        } finally {
            rollback(txn2);
        }
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNotNull(firstServiceController);
        assertNull(serviceRegistry.getService(firstSN));

        final BasicTransaction txn3 = newTransaction();
        try {
            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN, txn3);
            firstService = new SimpleTestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
        } finally {
            prepare(txn3);
            commit(txn3);
        }
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertNotNull(firstServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));

        final BasicTransaction txn4 = newTransaction();
        try {
            secondServiceController.remove(txn4);
        } finally {
            assertFalse(attemptToCommit(txn4));
        }
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn5 = newTransaction();
        try {
            firstServiceController.remove(txn5);
        } finally {
            prepare(txn5);
            commit(txn5);
        }
        assertFalse(firstService.isUp());
        assertTrue(secondService.isUp());
        assertNull(serviceRegistry.getService(firstSN));
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn6 = newTransaction();
        try {
            secondServiceController.remove(txn6);
        } finally {
            rollback(txn6);
        }
        assertTrue(secondService.isUp());
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final BasicTransaction txn7 = newTransaction();
        try {
            secondServiceController.remove(txn7);
        } finally {
            prepare(txn7);
            commit(txn7);
        }
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

}
