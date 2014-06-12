/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.msc.test.services;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.test.utils.AbstractServiceTest;
import org.jboss.msc.test.utils.TestService;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.jboss.msc.service.ServiceMode.ACTIVE;
import static org.jboss.msc.service.ServiceMode.LAZY;
import static org.jboss.msc.service.ServiceMode.ON_DEMAND;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Parent dependencies test case
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ParentDependencyTestCase extends AbstractServiceTest {

    private static final ServiceName firstSN = ServiceName.of("first");
    private static final ServiceName secondSN = ServiceName.of("second");
    private static final ServiceName thirdSN = ServiceName.of("third");

//    protected final TestService addChildService(final ServiceContext parentServiceContext,
//            final StartContext<Void> startContext, final ServiceName serviceName,
//            final ServiceMode serviceMode, final ServiceName parentDependency) throws InterruptedException, ExecutionException {
//        // new transaction
//        final UpdateTransaction txn = newUpdateTransaction();
//        try {
//            // obtain service builder from >> parent<< service context
//            final ServiceBuilder<Void> serviceBuilder = parentServiceContext.addService(serviceRegistry, serviceName, txn, startContext);
//            // create and set test service
//            final TestService service = new TestService(serviceName, serviceBuilder, false);
//            serviceBuilder.setService(service);
//            // set mode
//            if (serviceMode != null)
//                serviceBuilder.setMode(serviceMode);
//            // install
//            serviceBuilder.install();
//            // prepare transaction
//            prepare(txn);
//            // commit transaction
//            commit(txn);
//            // check that parent service is there, and that child service is installed and up
//            final TestService parentService = (TestService) serviceRegistry.getService(parentDependency).getService();
//            if (service.isUp() || (parentService != null && parentService.isUp())) throws InterruptedException, ExecutionException {
//                assertSame(service, serviceRegistry.getRequiredService(serviceName).getService());
//            } else {
//                assertNull(serviceRegistry.getService(serviceName));
//            }
//            return service;
//        } catch (final Exception e) throws InterruptedException, ExecutionException {
//            // always clear transaction if exception is detected
//            rollback(txn);
//            throw e;
//        }
//    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), <B>first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>attempt to remove parent before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase1() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        // request first service to add second service as a child
        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), <B>first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>attempt to remove parent before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase2() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), <B>first service</B>'s child</LI>
     * <LI>attempt to remove parent before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase3() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        // remove first service
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), <B> first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase4() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN,  LAZY,  serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertNotNull(secondService);
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        serviceRegistry.getRequiredService(thirdSN);
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), <B> first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase5() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, LAZY, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertNotNull(secondService);
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        serviceRegistry.getRequiredService(thirdSN);
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase6() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, LAZY, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), <B> first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase7() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ACTIVE, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), <B> first service</B>'s child</LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase8() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture =  firstService.addChild(secondSN, ACTIVE, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase9() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, ACTIVE, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on <B>first service</B></LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase10() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase11() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase12() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on <B>first service</B></LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase13() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, LAZY, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on <B>first service</B></LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase14() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, LAZY, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase15() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, ON_DEMAND, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertFalse(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on <B>first service</B></LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase16() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, ON_DEMAND);

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ACTIVE, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on unrequired <B>first service</B></LI>
     * <LI><B>third service</B>, (ACTIVE mode), <B>first service</B>'s dependent
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase17() throws InterruptedException, ExecutionException {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final Future<TestService> secondServiceFuture = firstService.addChild(secondSN, ACTIVE, serviceRegistry);

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
        serviceRegistry.getRequiredService(thirdSN);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase18() throws InterruptedException, ExecutionException {
        final TestService firstService;
        final Future<TestService> secondServiceFuture;
        final UpdateTransaction txn1 = newUpdateTransaction();
        try {
            final ServiceBuilder<Void> serviceBuilder = txnController.getServiceContext().addService(serviceRegistry, firstSN,  txn1);
            firstService = new TestService(firstSN, serviceBuilder, false);
            serviceBuilder.setService(firstService);
            secondServiceFuture = firstService.addChild(secondSN, ACTIVE, serviceRegistry);
            serviceBuilder.install();
        } finally {
            prepare(txn1);
            commit(txn1);
        }
        assertTrue(firstService.isUp());

        final TestService secondService = secondServiceFuture.get();
        assertTrue(secondService.isUp());
        serviceRegistry.getRequiredService(secondSN);

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertNull(serviceRegistry.getService(secondSN));
    }
}