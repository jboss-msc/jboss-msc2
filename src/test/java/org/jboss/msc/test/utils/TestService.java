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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContext;
import org.jboss.msc.service.ServiceMode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;

/**
 * Basic service for tests.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TestService implements Service<Void> {
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch stopLatch = new CountDownLatch(1);

    private final ServiceName serviceName;
    private final ServiceContext serviceContext;
    private final Dependency<?>[] dependencies;
    private final boolean failToStart;
    private AtomicBoolean up = new AtomicBoolean();
    private AtomicBoolean failed = new AtomicBoolean();
    private Collection<ChildServiceFactory> childServiceFactories = Collections.emptyList();

    public TestService(ServiceName serviceName, ServiceBuilder<Void> serviceBuilder, final boolean failToStart,
            final DependencyInfo<?>... dependencyInfos) {
        this.serviceContext = serviceBuilder.getServiceContext();
        this.serviceName = serviceName;
        assertNotNull(serviceContext);
        this.failToStart = failToStart;
        this.dependencies = new Dependency[dependencyInfos.length];
        for (int i = 0; i < dependencies.length; i++) {
            dependencies[i] = dependencyInfos[i].add(serviceBuilder);
        }
    }

    @Override
    public void start(final StartContext<Void> context) {
        assertFalse(up.get() || failed.get());
        if (failToStart) {
            failed.set(true);
            // context.addProblem(new UnsupportedOperationException());
            context.fail();
        } else {
            for (ChildServiceFactory childFactory: childServiceFactories) {
                childFactory.create(context, serviceContext);
            }
            up.set(true);
            context.complete();
        }
        startLatch.countDown();
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

    public synchronized Future<TestService> addChild(ServiceName name, ServiceMode serviceMode,  ServiceRegistry registry) {
        if (childServiceFactories.isEmpty()) {
            childServiceFactories = new ArrayList<ChildServiceFactory>();
        }
        ChildServiceFactory childServiceFactory = new ChildServiceFactory(name, serviceMode, registry);
        childServiceFactories.add(childServiceFactory);
        return childServiceFactory;
    }

    public boolean isFailed() {
        return failed.get();
    }

    public boolean isUp() {
        return up.get();
    }

    public ServiceContext getServiceContext() {
        return serviceContext;
    }

    public Dependency<?> getDependency(int index) {
        return dependencies[index];
    }

    public String toString() {
        return serviceName.toString();
    }

    @Override
    public void stop(StopContext stopContext) {
        assertTrue(up.get() || failed.get());
        up.set(false);
        failed.set(false);
        stopContext.complete();
        stopLatch.countDown();
    }

    private static class ChildServiceFactory implements Future<TestService> {
        private final ServiceName serviceName;
        private final ServiceRegistry registry;
        private final ServiceMode serviceMode;
        private final CountDownLatch latch;
        private TestService childService;

        ChildServiceFactory(final ServiceName serviceName, final ServiceMode serviceMode, final ServiceRegistry registry) {
            this.serviceName = serviceName;
            this.registry = registry;
            this.serviceMode = serviceMode;
            latch = new CountDownLatch(1);
        }

        public void create(StartContext<?> startContext, ServiceContext parentContext) {
            final ServiceBuilder<Void> childBuilder = startContext.addService(registry, serviceName, parentContext);
            childBuilder.setMode(serviceMode);
            childService = new TestService(serviceName, childBuilder, false);
            childBuilder.setService(childService);
            childBuilder.install();
            latch.countDown();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return  childService != null;//latch.getCount() == 0;
        }

        @Override
        public TestService get() throws InterruptedException, ExecutionException {
            latch.await();
            return childService;
        }

        @Override
        public TestService get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            latch.await(timeout, unit);
            return childService;
        }
    }
}
