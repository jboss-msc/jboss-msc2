/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.jboss.msc.service;

import static java.lang.System.getSecurityManager;
import static java.security.AccessController.doPrivileged;

import java.lang.reflect.Constructor;
import java.security.PrivilegedAction;

/**
 * A {@link ServiceContainer} factory. This singleton is thread safe.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ServiceContainerFactory {

    private static final ServiceContainerFactory instance = new ServiceContainerFactory();

    /**
     * Gets container factory instance.
     *
     * @return container factory instance
     */
    public static ServiceContainerFactory getInstance() {
        return instance;
    }

    private ServiceContainerFactory() {}

    /**
     * Creates new service container.
     *
     * @return a reference to this object
     */
    public ServiceContainer newServiceContainer() {
        final SecurityManager sm = getSecurityManager();
        if (sm != null) {
            return doPrivileged(new PrivilegedAction<ServiceContainer>() {
                public ServiceContainer run() {
                    return invokeConstructor();
                }
            });
        } else {
            return invokeConstructor();
        }
    }

    private static final String IMPL_CLASS_NAME = "org.jboss.msc.txn.ServiceContainerImpl";
    private static Constructor<? extends ServiceContainer> IMPL_CLASS_CONSTRUCTOR;

    static {
        final SecurityManager sm = getSecurityManager();
        if (sm != null) {
            IMPL_CLASS_CONSTRUCTOR = doPrivileged(new PrivilegedAction<Constructor<? extends ServiceContainer>>() {
                public Constructor<? extends ServiceContainer> run() {
                    return getConstructor();
                }
            });
        } else {
            IMPL_CLASS_CONSTRUCTOR = getConstructor();
        }
    }

    private static Constructor<? extends ServiceContainer> getConstructor() {
        try {
            final ClassLoader loader = ServiceContainerFactory.class.getClassLoader();
            Constructor<? extends ServiceContainer> ctor = Class.forName(IMPL_CLASS_NAME, false, loader)
                                         .asSubclass(ServiceContainer.class)
                                             .getDeclaredConstructor(new Class<?>[0]);
            ctor.setAccessible(true);
            return ctor;
        } catch (final Throwable ignored) {
            // TODO: log it
            return null;
        }
    }

    private static ServiceContainer invokeConstructor() {
        try {
            return IMPL_CLASS_CONSTRUCTOR.newInstance();
        } catch (final Throwable ignored) {
            // TODO: log it
            return null;
        }
    }

}
