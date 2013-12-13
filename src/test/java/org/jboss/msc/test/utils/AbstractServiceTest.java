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

import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceContainerFactory;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.test.utils.TestService.DependencyInfo;
import org.jboss.msc.txn.BasicTransaction;
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

    protected static final DependencyFlag[] requiredFlag = new DependencyFlag[] { DependencyFlag.REQUIRED };
    protected static final DependencyFlag[] unrequiredFlag = new DependencyFlag[] { DependencyFlag.UNREQUIRED };

    protected volatile ServiceContainer serviceContainer;
    protected volatile ServiceRegistry serviceRegistry;

    static {
        TestServiceBuilder.setDefaultTransactionController(AbstractTransactionTest.txnController);
    }

    @Override
    @Before
    public void setUp() {
        super.setUp();
        serviceContainer = ServiceContainerFactory.getInstance().newServiceContainer();
        serviceRegistry = serviceContainer.newRegistry();
        TestServiceBuilder.setDefaultServiceRegistry(serviceRegistry);
    }

    @Override
    @After
    public void tearDown() {
        shutdownContainer();
        super.tearDown();
    }

    protected final void removeRegistry() {
        removeRegistry(serviceRegistry);
    }

    protected final void removeRegistry(final ServiceRegistry serviceRegistry) {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new RemoveRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
    }

    protected final void enableRegistry() {
        enableRegistry(serviceRegistry);
    }

    protected final void enableRegistry(final ServiceRegistry serviceRegistry) {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new EnableRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
    }

    protected final void disableRegistry() {
        disableRegistry(serviceRegistry);
    }

    protected final void disableRegistry(final ServiceRegistry serviceRegistry) {
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new DisableRegistryTask(serviceRegistry, txn)).release();
        prepare(txn);
        commit(txn);
    }

    protected final void shutdownContainer() {
        shutdownContainer(serviceContainer);
    }

    protected final void shutdownContainer(final ServiceContainer serviceContainer) {
        final BasicTransaction txn = newTransaction();
        try {
            serviceContainer.shutdown(txn);
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode) {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, new DependencyInfo[0]);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode, final ServiceName... dependencies) {
        // new transaction
        final BasicTransaction txn = newTransaction();
        // create service builder
        final TestServiceBuilder serviceBuilder = new TestServiceBuilder(txn, serviceRegistry, serviceName, failToStart,
                serviceMode, dependencies);
        // install
        final ServiceController serviceController = serviceBuilder.install();
        assertNotNull(serviceController);
        final TestService service = serviceBuilder.getService();
        // attempt to commit or check aborted installation
        if (attemptToCommit(txn)) {
            assertSame(service, serviceRegistry.getRequiredService(serviceName).getService());
            return service;
        } else {
            assertNull(serviceRegistry.getService(serviceName));
            assertFalse(service.isUp());
            return null;
        }
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode, final DependencyFlag[] dependencyFlags,
            final ServiceName... dependencies) {
        // create dependency info array (the service contructor is responsible for adding dependencies, these objects will be
        // used for that)
        DependencyInfo<?>[] dependencyInfos = new DependencyInfo<?>[dependencies.length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencyInfos[i] = new DependencyInfo<Void>(dependencies[i], dependencyFlags);
        }
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencyInfos);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) {
        return addService(serviceRegistry, serviceName, false, ServiceMode.ACTIVE);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final DependencyInfo<?>... dependencies) {
        return addService(serviceRegistry, serviceName, false, ServiceMode.ACTIVE, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceMode serviceMode, final DependencyInfo<?>... dependencies) {
        // new transaction
        final BasicTransaction txn = newTransaction();
        TestService service = null;
        final boolean committed;
        try {
            // create service builder
            final TestServiceBuilder serviceBuilder = new TestServiceBuilder(txn, serviceRegistry, serviceName, failToStart,
                    serviceMode, dependencies);
            // install
            final ServiceController serviceController = serviceBuilder.install();
            assertNotNull(serviceController);
            service = serviceBuilder.getService();
        } finally {
            // attempt to commit or check aborted installation
            committed = attemptToCommit(txn);
        }
        if (committed) {
            assertSame(service, serviceRegistry.getRequiredService(serviceName).getService());
            return service;
        } else {
            assertNull(serviceRegistry.getService(serviceName));
            assertFalse(service.isUp());
            return null;
        }
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final ServiceMode serviceMode, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, false, null, dependencies);
    }

    protected final TestService addService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final boolean failToStart, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, failToStart, null, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart,
            final ServiceMode serviceMode, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart,
            final ServiceMode serviceMode, final DependencyFlag[] dependencyFlags, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, failToStart, serviceMode, dependencyFlags, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceMode serviceMode,
            final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceMode serviceMode,
            final DependencyFlag[] dependencyFlags, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, false, serviceMode, dependencyFlags, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, false, null, dependencies);
    }

    protected final TestService addService(final ServiceName serviceName, final boolean failToStart,
            final ServiceName... dependencies) {
        return addService(serviceRegistry, serviceName, failToStart, null, dependencies);
    }

    protected final boolean removeService(final ServiceRegistry serviceRegistry, final ServiceName serviceName,
            final TestService service) {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new RemoveServiceTask(serviceRegistry, serviceName, service, txn)).release();
        if (attemptToCommit(txn)) {
            assertNull(serviceRegistry.getService(serviceName));
            return true;
        } else {
            assertNotNull(serviceRegistry.getRequiredService(serviceName));
            return false;
        }
    }

    protected final boolean removeService(final ServiceName serviceName, final TestService service) {
        return removeService(serviceRegistry, serviceName, service);
    }

    protected final void enableService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new EnableServiceTask(serviceRegistry, serviceName, txn)).release();
        prepare(txn);
        commit(txn);
        assertNull(serviceRegistry.getService(serviceName));
    }

    protected final void enableService(final ServiceName serviceName) {
        enableService(serviceRegistry, serviceName);
    }

    protected final void disableService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) {
        assertNotNull(serviceRegistry.getService(serviceName));
        final BasicTransaction txn = newTransaction();
        txnController.newTask(txn, new DisableServiceTask(serviceRegistry, serviceName, txn)).release();
        prepare(txn);
        commit(txn);
        assertNull(serviceRegistry.getService(serviceName));
    }

    protected final void disableService(final ServiceName serviceName) {
        enableService(serviceRegistry, serviceName);
    }

    protected final TestService getService(final ServiceName serviceName) {
        return getService(serviceRegistry, serviceName);
    }

    protected final TestService getService(final ServiceRegistry serviceRegistry, final ServiceName serviceName) {
        final ServiceController controller = serviceRegistry.getService(serviceName);
        return (TestService) (controller != null ? controller.getService() : null);
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

}
