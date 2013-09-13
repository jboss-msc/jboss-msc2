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

package org.jboss.msc.test.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.List;

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceContainerFactory;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.test.utils.TestService.DependencyInfo;
import org.jboss.msc.txn.BasicTransaction;
import org.jboss.msc.txn.Problem;
import org.jboss.msc.txn.Problem.Severity;
import org.jboss.msc.txn.ServiceContext;
import org.jboss.msc.txn.ServiceController;
import org.jboss.msc.txn.TransactionController;
import org.junit.After;
import org.junit.Before;

/**
 * Test base used for service test cases.
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class AbstractServiceTest extends AbstractTransactionTest {

    protected static final DependencyFlag[] requiredFlag = new DependencyFlag[] {DependencyFlag.REQUIRED};
    protected static final DependencyFlag[] unrequiredFlag = new DependencyFlag[] {DependencyFlag.UNREQUIRED};

    protected volatile ServiceContainer serviceContainer;
    protected volatile ServiceRegistry serviceRegistry;

    static {
        TestServiceBuilder.setDefaultTransactionController(AbstractTransactionTest.txnController);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        serviceContainer = ServiceContainerFactory.getInstance().newServiceContainer();
        serviceRegistry = serviceContainer.newRegistry();
        TestServiceBuilder.setDefaultServiceRegistry(serviceRegistry);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        shutdownContainer();
        super.tearDown();
    }

    protected final void removeRegistry() throws Exception {
        removeRegistry(serviceRegistry);
    }

    protected final void removeRegistry(final ServiceRegistry serviceRegistry) throws Exception {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new RemoveRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
    }

    protected final void enableRegistry() throws Exception {
        enableRegistry(serviceRegistry);
    }

    protected final void enableRegistry(final ServiceRegistry serviceRegistry) throws Exception {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new EnableRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
    }

    protected final void disableRegistry() throws Exception {
        disableRegistry(serviceRegistry);
    }

    protected final void disableRegistry(final ServiceRegistry serviceRegistry) throws Exception {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new DisableRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
    }

    protected final void shutdownContainer() throws Exception {
        shutdownContainer(serviceContainer);
    }

    protected final void shutdownContainer(final ServiceContainer serviceContainer) throws Exception {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new ShutdownContainerTask(serviceContainer, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode) throws InterruptedException {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, new DependencyInfo[0]);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode, final ServiceName... dependencies) throws InterruptedException {
        // new transaction
        final BasicTransaction txn = newTransaction();
        // create service builder
        final TestServiceBuilder serviceBuilder = new TestServiceBuilder(txn, serviceRegistry, serviceName, failToStart, serviceMode, dependencies);
        // install
        final ServiceController serviceController = serviceBuilder.install();
        assertNotNull(serviceController);
        final TestService service = serviceBuilder.getService();
        // attempt to commit or check aborted installation
        if (attemptToCommit(txn)) {
            assertSame(service, serviceRegistry.getRequiredService(serviceName));
            return service;
        } else {
            assertNull(serviceRegistry.getService(serviceName));
            assertFalse(service.isUp());
            return null;
        }
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode, final DependencyFlag[] dependencyFlags, final ServiceName... dependencies) throws InterruptedException {
        // create dependency info array (the service contructor is responsible for adding dependencies, these objects will be used for that)
        DependencyInfo<?>[] dependencyInfos = new DependencyInfo<?>[dependencies.length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencyInfos[i] = new DependencyInfo<Void>(dependencies[i], dependencyFlags);
        }
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencyInfos);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, ServiceMode.ACTIVE);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final DependencyInfo<?>... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, ServiceMode.ACTIVE, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode, final DependencyInfo<?>... dependencies) throws InterruptedException {
        // new transaction
        final BasicTransaction txn = newTransaction();
        // create service builder
        final TestServiceBuilder serviceBuilder = new TestServiceBuilder(txn, serviceRegistry, serviceName, failToStart, serviceMode, dependencies);
        // install
        final ServiceController serviceController = serviceBuilder.install();
        assertNotNull(serviceController);
        final TestService service = serviceBuilder.getService();
        // attempt to commit or check aborted installation
        if (attemptToCommit(txn)) {
            assertSame(service, serviceRegistry.getRequiredService(serviceName));
            return service;
        } else {
            assertNull(serviceRegistry.getService(serviceName));
            assertFalse(service.isUp());
            return null;
        }
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final ServiceMode serviceMode, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, null, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final boolean failToStart, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, failToStart, null, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart, final ServiceMode serviceMode, final DependencyFlag[] dependencyFlags, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencyFlags, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceMode serviceMode, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceMode serviceMode, final DependencyFlag[] dependencyFlags, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencyFlags, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, false, null, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart, final ServiceName... dependencies) throws InterruptedException {
        return addService(serviceRegistry, serviceName, failToStart, null, dependencies);
    }

    protected final boolean removeService(final ServiceRegistry serviceRegistry, final ServiceName serviceName, final TestService service) throws InterruptedException {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new RemoveServiceTask(serviceRegistry, serviceName, service, txn)).release();
        if (attemptToCommit(txn)) {
            assertNoCriticalProblems(txn);
            assertNull(serviceRegistry.getService(serviceName));
            return true;
        } else {
            assertNotNull(serviceRegistry.getRequiredService(serviceName));
            return false;
        }
    }

    protected final boolean removeService(final ServiceName serviceName, final TestService service) throws InterruptedException {
        return removeService(serviceRegistry, serviceName, service);
    }

    protected final void enableService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) throws InterruptedException {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new EnableServiceTask(serviceRegistry, serviceName, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
        assertNull(serviceRegistry.getService(serviceName));
    }

    protected final void enableService(final ServiceName serviceName) throws InterruptedException {
        enableService(serviceRegistry, serviceName);
    }
    
    protected final void disableService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) throws InterruptedException {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new DisableServiceTask(serviceRegistry, serviceName, txn)).release();
        prepare(txn);
        commit(txn);
        assertNoCriticalProblems(txn);
        assertNull(serviceRegistry.getService(serviceName));
    }

    protected final void disableService(final ServiceName serviceName) throws InterruptedException {
        enableService(serviceRegistry, serviceName);
    }
    
    protected final TestService getService(final ServiceName serviceName) throws InterruptedException {
        return getService(serviceRegistry, serviceName);
    }
    
    protected final TestService getService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) throws InterruptedException {
        return (TestService) serviceRegistry.getService(serviceName); 
    }

    protected void assertServiceContext(ServiceContext serviceContext) {
        assertNotNull(serviceContext);
        // try to use with wrong transactions
        final TransactionController outsiderController = TransactionController.createInstance();
        final BasicTransaction outsiderTransaction = outsiderController.create(defaultExecutor);
        final ServiceName serviceName = ServiceName.of("non", "existent");
        try {
            IllegalArgumentException expected = null;
            try {
                serviceContext.addService(serviceRegistry, serviceName, outsiderTransaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);

            expected = null;
            try {
                serviceContext.addService(TestService.class, serviceRegistry, serviceName, outsiderTransaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
            
            expected = null;
            try {
                serviceContext.getReportableContext(outsiderTransaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }

        } finally {
            outsiderController.rollback(outsiderTransaction, null);
        }

        IllegalArgumentException expected = null;
        try {
            serviceContext.addService(serviceRegistry, serviceName, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            serviceContext.addService(TestService.class, serviceRegistry, serviceName, null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            serviceContext.getReportableContext(null);
        } catch (IllegalArgumentException e) {
            expected = e;
        }

        final BasicTransaction transaction = newTransaction();
        try {
            expected = null;
            try {
                serviceContext.addService(null, serviceName, transaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
    
            expected = null;
            try {
                serviceContext.addService(TestService.class, null, serviceName, transaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
    
            expected = null;
            try {
                serviceContext.addService(serviceRegistry, null, transaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
    
            expected = null;
            try {
                serviceContext.addService(TestService.class, serviceRegistry, null, transaction);
            } catch (IllegalArgumentException e) {
                expected = e;
            }
            assertNotNull(expected);
        } finally {
            txnController.rollback(transaction, null);
        }
    }

    private void assertNoCriticalProblems(final BasicTransaction txn) {
        List<Problem> problems = txnController.getProblemReport(txn).getProblems();
        for (final Problem problem : problems) {
            if (problem.getSeverity() == Severity.CRITICAL) {
                fail("Critical problem detected");
            }
        }
    }

}
