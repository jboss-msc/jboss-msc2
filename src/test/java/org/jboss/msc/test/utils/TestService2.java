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

import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.txn.ServiceContext;

/**
 * Basic service for tests.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TestService2 implements Service<Void> {

    private final ServiceContext serviceContext;
    private final Dependency[] dependencies;
    private final boolean failToStart; 
    private AtomicBoolean up = new AtomicBoolean();
    private AtomicBoolean failed = new AtomicBoolean();

    public TestService2(ServiceBuilder<TestService2> serviceBuilder, final boolean failToStart, final DependencyInfo<?>... dependencyInfos) {
        this.serviceContext = serviceBuilder.getServiceContext();
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
            //context.addProblem(new UnsupportedOperationException());
            context.fail();
        } else {
            up.set(true);
            context.complete();
        }
    }

    @Override
    public void stop(final StopContext context) {
        assertTrue(up.get() || failed.get());
        up.set(false);
        failed.set(false);
        context.complete();
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

    private static class DependencyInfo<T> {
        private final ServiceName name;
        private final ServiceRegistry registry;
        private final DependencyFlag[] flags;

        public DependencyInfo(ServiceName name, ServiceRegistry registry, DependencyFlag... flags) {
            assertNotNull(name);
            this.name = name;
            this.registry = registry;
            this.flags = flags;
        }

        public Dependency<T> add(ServiceBuilder<?> serviceBuilder) {
            // make sure we support all signatures so that test can invoke any of them, thus garanteeing coverage
            if (registry == null) {
                if (flags == null) {
                    return serviceBuilder.addDependency(name);
                } else {
                    return serviceBuilder.addDependency(name, flags);
                }
            } else {
                if (flags == null) {
                    return serviceBuilder.addDependency(registry, name);
                } else {
                    return serviceBuilder.addDependency(registry, name, flags);
                }
            }
        }
    }
}
