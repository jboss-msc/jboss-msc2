/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.AbstractTransactionTest;
import org.jboss.msc.txn.UpdateTransaction;
import org.jboss.msc.util.CompletionListener;
import org.jboss.msc.util.Listener;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class ServiceControllerTestCase extends AbstractTransactionTest {

    @Test
    public void replaceStartedServiceWithNewService() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.newUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.newServiceContainer(updateTxn);
        final ServiceRegistry registry = container.newRegistry(updateTxn);
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder<Void> sb = txnController.newServiceContext(updateTxn).addService(registry, serviceName);
        final TestService<Void> service1 = new TestService<>(serviceName);
        final ServiceController<Void> serviceController = sb.setService(service1).setMode(ServiceMode.ACTIVE).install();
        // assert first service is up and running
        prepare(updateTxn);
        commit(updateTxn);
        service1.waitStart();
        assertTrue(service1.isUp());
        assertSame(service1, serviceController.getService());
        // next transation
        updateTxn = newUpdateTransaction();
        // replace service
        final TestService<Void> service2 = new TestService<>(serviceName);
        final ReplaceListener<Void> listener = new ReplaceListener<>();
        serviceController.replace(updateTxn, service1, service2, listener);
        prepare(updateTxn);
        commit(updateTxn);
        service1.waitStop();
        service2.waitStart();
        // assert first service down and removed
        assertFalse(service1.isUp());
        assertNotSame(service1, serviceController.getService());
        // assert second service up and running
        assertTrue(service2.isUp());
        assertSame(service2, serviceController.getService());
        assertTrue(listener.wasExecuted());
    }

    @Test
    public void replaceStartedServiceWithNullService() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.newUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.newServiceContainer(updateTxn);
        final ServiceRegistry registry = container.newRegistry(updateTxn);
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder<Void> sb = txnController.newServiceContext(updateTxn).addService(registry, serviceName);
        final TestService<Void> service1 = new TestService<>(serviceName);
        final ServiceController<Void> serviceController = sb.setService(service1).setMode(ServiceMode.ACTIVE).install();
        // assert first service is up and running
        prepare(updateTxn);
        commit(updateTxn);
        service1.waitStart();
        assertTrue(service1.isUp());
        assertSame(service1, serviceController.getService());
        // next transation
        updateTxn = newUpdateTransaction();
        // replace service
        final ReplaceListener<Void> listener = new ReplaceListener<>();
        serviceController.replace(updateTxn, service1, null, listener);
        prepare(updateTxn);
        commit(updateTxn);
        // assert first service down and removed
        assertFalse(service1.isUp());
        assertNotSame(service1, serviceController.getService());
        // assert second service up and null
        assertSame(null, serviceController.getService());
        assertTrue(listener.wasExecuted());
    }

    @Test
    public void replaceDownServiceWithNewService() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.newUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.newServiceContainer(updateTxn);
        final ServiceRegistry registry = container.newRegistry(updateTxn);
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder<Void> sb = txnController.newServiceContext(updateTxn).addService(registry, serviceName);
        final TestService<Void> service1 = new TestService<>(serviceName);
        final ServiceController<Void> serviceController = sb.setService(service1).setMode(ServiceMode.LAZY).install();
        // assert first service is up and running
        prepare(updateTxn);
        commit(updateTxn);
        assertFalse(service1.isUp());
        assertSame(service1, serviceController.getService());
        // next transation
        updateTxn = newUpdateTransaction();
        // replace service
        final TestService<Void> service2 = new TestService<>(serviceName);
        final ReplaceListener<Void> listener = new ReplaceListener<>();
        serviceController.replace(updateTxn, service1, service2, listener);
        prepare(updateTxn);
        commit(updateTxn);
        // assert first service down and removed
        assertFalse(service1.isUp());
        assertNotSame(service1, serviceController.getService());
        // assert second service up and running
        assertFalse(service2.isUp());
        assertSame(service2, serviceController.getService());
        assertTrue(listener.wasExecuted());
    }

    @Test
    public void replaceDownServiceWithNullService() throws Exception {
        final CompletionListener<UpdateTransaction> createListener = new CompletionListener<>();
        txnController.newUpdateTransaction(defaultExecutor, createListener);
        UpdateTransaction updateTxn = createListener.awaitCompletion();
        assertNotNull(updateTxn);
        final ServiceContainer container = txnController.newServiceContainer(updateTxn);
        final ServiceRegistry registry = container.newRegistry(updateTxn);
        final ServiceName serviceName = ServiceName.of("test");
        final ServiceBuilder<Void> sb = txnController.newServiceContext(updateTxn).addService(registry, serviceName);
        final TestService<Void> service1 = new TestService<>(serviceName);
        final ServiceController<Void> serviceController = sb.setService(service1).setMode(ServiceMode.LAZY).install();
        // assert first service is up and running
        prepare(updateTxn);
        commit(updateTxn);
        assertFalse(service1.isUp());
        assertSame(service1, serviceController.getService());
        // next transation
        updateTxn = newUpdateTransaction();
        // replace service
        final ReplaceListener<Void> listener = new ReplaceListener<>();
        serviceController.replace(updateTxn, service1, null, listener);
        prepare(updateTxn);
        commit(updateTxn);
        // assert first service down and removed
        assertFalse(service1.isUp());
        assertNotSame(service1, serviceController.getService());
        // assert second service up and null
        assertSame(null, serviceController.getService());
        assertTrue(listener.wasExecuted());
    }

    private static final class ReplaceListener<T> implements Listener<ServiceController<T>> {

        private boolean executed;

        @Override
        public void handleEvent(final ServiceController<T> result) {
            executed = true;
        }

        private boolean wasExecuted() {
            return executed;
        }
    }

    public final class TestService<T> implements Service<T> {
        private CountDownLatch startLatch = new CountDownLatch(1);
        private CountDownLatch stopLatch = new CountDownLatch(1);
        private final ServiceName serviceName;
        private AtomicBoolean up = new AtomicBoolean();

        public TestService(ServiceName serviceName) {
            this.serviceName = serviceName;
        }

        @Override
        public void start(final StartContext<T> context) {
            assertFalse(up.get());
            up.set(true);
            context.complete();
            startLatch.countDown();
        }

        public boolean isUp() {
            return up.get();
        }

        public String toString() {
            return serviceName.toString();
        }

        @Override
        public void stop(StopContext stopContext) {
            assertTrue(up.get());
            up.set(false);
            stopContext.complete();
            stopLatch.countDown();
        }

        public void waitStart() {
            while (true) {
                try {
                    startLatch.await();
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        public void waitStop() {
            while (true) {
                try {
                    stopLatch.await();
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }

}
