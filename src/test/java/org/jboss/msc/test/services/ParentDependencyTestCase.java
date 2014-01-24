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

import static org.jboss.msc.service.ServiceMode.ACTIVE;
import static org.jboss.msc.service.ServiceMode.LAZY;
import static org.jboss.msc.service.ServiceMode.ON_DEMAND;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.test.utils.AbstractServiceTest;
import org.jboss.msc.test.utils.TestService;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.CommitResult;
import org.jboss.msc.txn.CompletionListener;
import org.jboss.msc.txn.Problem.Severity;
import org.jboss.msc.txn.ReportableContext;
import org.junit.Ignore;
import org.junit.Test;

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

    protected final TestService addChildService(final ServiceContext parentServiceContext, final ServiceName serviceName,
            final ServiceMode serviceMode, final ServiceName parentDependency) {
        // new transaction
        final BasicTransaction txn = newTransaction();
        try {
            // obtain service builder from >> parent<< service context
            final ServiceBuilder<Void> serviceBuilder = parentServiceContext.addService(serviceRegistry, serviceName, txn);
            // create and set test service
            final TestService service = new TestService(serviceName, serviceBuilder, false);
            serviceBuilder.setService(service);
            // set mode
            if (serviceMode != null)
                serviceBuilder.setMode(serviceMode);
            // install
            serviceBuilder.install();
            // prepare transaction
            prepare(txn);
            // commit transaction
            commit(txn);
            // check that parent service is there, and that child service is installed and up
            final TestService parentService = (TestService) serviceRegistry.getService(parentDependency).getService();
            if (service.isUp() || (parentService != null && parentService.isUp())) {
                assertSame(service, serviceRegistry.getRequiredService(serviceName).getService());
            } else {
                assertNull(serviceRegistry.getService(serviceName));
            }
            return service;
        } catch (final Exception e) {
            // always clear transaction if exception is detected
            rollback(txn);
            throw e;
        }
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), <B>first service</B>'s child</LI>
     * <LI>attempt to remove parent before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase1() {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), <B>first service</B>'s child</LI>
     * <LI>attempt to remove parent before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase2() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
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
    public void usecase3() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase4() {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service now
        expected = null;
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase5() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        ;
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service with parent removed
        expected = null;
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
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
    public void usecase6() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase7() {
        final TestService firstService = addService(firstSN, ON_DEMAND);

        final ServiceContext parentContext = firstService.getServiceContext();

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service now
        expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), <B> first service</B>'s child</LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase8() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service now
        expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
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
    public void usecase9() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase10() {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service now
        expected = null;
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase11() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        // cannot add child service now
        expected = null;
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ON_DEMAND mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase12() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        // cannot add child service now
        IllegalStateException expected = null;
        try {
            addChildService(parentContext, secondSN, ON_DEMAND, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase13() {
        final TestService firstService = addService(firstSN, ON_DEMAND);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase14() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(thirdService.isUp());

        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (LAZY mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase15() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, LAZY, firstSN);
        assertTrue(firstService.isUp());
        assertFalse(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        IllegalStateException expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, LAZY, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ON_DEMAND mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase16() {
        final TestService firstService = addService(firstSN, ON_DEMAND);

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(thirdService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (LAZY mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase17() {
        final TestService firstService = addService(firstSN, LAZY);
        assertFalse(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
        assertFalse(firstService.isUp());

        final TestService thirdService = addService(thirdSN, ACTIVE, unrequiredFlag, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(thirdService.isUp());

        assertServiceContext(parentContext);

        // now we can add second service
        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(thirdService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());
        assertFalse(thirdService.isUp());

        expected = null;
        // cannot add a child service using a removed service context
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    /**
     * Usecase:
     * <UL>
     * <LI><B>first service</B> (ACTIVE mode), no dependencies</LI>
     * <LI><B>second service</B> (ACTIVE mode), depends on unrequired <B>first service</B></LI>
     * <LI>parent removed before container is shut down</LI>
     * </UL>
     */
    @Test
    public void usecase18() {
        final TestService firstService = addService(firstSN, ACTIVE);
        assertTrue(firstService.isUp());

        final ServiceContext parentContext = firstService.getServiceContext();
        assertServiceContext(parentContext);

        final TestService secondService = addChildService(parentContext, secondSN, ACTIVE, firstSN);
        assertTrue(firstService.isUp());
        assertTrue(secondService.isUp());
        assertTrue(removeService(firstSN, firstService));
        assertFalse(firstService.isUp());
        assertFalse(secondService.isUp());

        IllegalStateException expected = null;
        // cannot add a child service using a down service context
        try {
            addChildService(parentContext, secondSN, ACTIVE, firstSN);
        } catch (IllegalStateException e) {
            expected = e;
        }
        assertNotNull(expected);
    }
}