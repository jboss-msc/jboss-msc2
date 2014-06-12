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

import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;

import static org.junit.Assert.assertNotNull;

/**
 * Utilitary dependency info class used by {@link TestService} and {@link SimpleTestService} classes.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 *
 */
public class DependencyInfo<T> {
    private final ServiceName name;
    private final ServiceRegistry registry;
    private final DependencyFlag[] flags;

    public DependencyInfo(ServiceName name) {
        this(name, null, (DependencyFlag[]) null);
    }

    public DependencyInfo(ServiceName name, DependencyFlag... flags) {
        this(name, null, flags);
    }

    public DependencyInfo(ServiceName name, ServiceRegistry registry, DependencyFlag... flags) {
        assertNotNull(name);
        this.name = name;
        this.registry = registry;
        this.flags = flags;
    }

    public Dependency<T> add(ServiceBuilder<?> serviceBuilder) {
        // make sure we support all signatures so that test can invoke any of them, thus guaranteeing coverage
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
