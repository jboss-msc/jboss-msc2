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
package org.jboss.msc.test;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.txn.AbstractServiceTest;
import org.jboss.msc.txn.DependencyInfo;
import org.jboss.msc.txn.TestService;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@code Service}.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
public class ServiceTestCase extends AbstractServiceTest {

    private static final ServiceName firstSN = ServiceName.of("first");
    private static final ServiceName secondSN = ServiceName.of("second");

    @Test
    public void installAndRemoveService() {
        final UpdateTransaction txn1 = newUpdateTransaction();
        final TestService service;
        final ServiceController firstServiceController;
        try {
            ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext(txn1).addService(serviceRegistry, firstSN);
            service = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(service);
            firstServiceController = serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(service.isUp());
        assertNotNull(firstServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));

        final UpdateTransaction txn2 = newUpdateTransaction();
        try {
            firstServiceController.remove(txn2);
        } finally {
            prepare(txn2);
            commit(txn2);
        }
        assertFalse(service.isUp());
        assertNull(serviceRegistry.getService(firstSN));
    }

    @Test
    public void installAndRemoveServiceDependent() {
        final UpdateTransaction txn1 = newUpdateTransaction();
        final TestService firstService;
        final TestService secondService;
        final ServiceController firstServiceController;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext(txn1).addService(serviceRegistry, secondSN);
            secondService = new TestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceController = secondServiceBuilder.install();

            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext(txn1).addService(serviceRegistry, firstSN);
            firstService = new TestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
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

        final UpdateTransaction txn2 = newUpdateTransaction();
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
    public void installAndRemoveServiceDependentMultipleTxns() {
        final UpdateTransaction txn1 = newUpdateTransaction();
        final TestService secondService;
        final ServiceController secondServiceController;
        try {
            final ServiceBuilder<Void> secondServiceBuilder = txnController.getServiceContext(txn1).addService(serviceRegistry, secondSN);
            secondService = new TestService(secondSN, secondServiceBuilder, false);
            secondServiceBuilder.setService(secondService);
            secondServiceController = secondServiceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(secondService.isUp());
        assertNotNull(secondServiceController);
        assertSame(secondServiceController, serviceRegistry.getService(secondSN));

        final UpdateTransaction txn2 = newUpdateTransaction();
        final TestService firstService;
        final ServiceController firstServiceController;
        try {
            final ServiceBuilder<Void> firstServiceBuilder = txnController.getServiceContext(txn2).addService(serviceRegistry, firstSN);
            firstService = new TestService(firstSN, firstServiceBuilder, false, new DependencyInfo<Void>(secondSN));
            firstServiceBuilder.setService(firstService);
            firstServiceController = firstServiceBuilder.install();
        } finally {
            prepare(txn2);
            commit(txn2);
        }
        assertTrue(firstService.isUp());
        assertNotNull(firstServiceController);
        assertSame(firstServiceController, serviceRegistry.getService(firstSN));

        final UpdateTransaction txn3 = newUpdateTransaction();
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
        final UpdateTransaction txn4 = newUpdateTransaction();
        try {
            secondServiceController.remove(txn4);
        } finally {
            prepare(txn4);
            commit(txn4);
        }
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }
}
