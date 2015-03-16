/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
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

import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.txn.AbstractServiceTest;
import org.jboss.msc.txn.UpdateTransaction;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Service dependencies cycle detection tests.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class DependenciesCycleTestCase extends AbstractServiceTest {

    private static final ServiceName A = ServiceName.of("A");
    private static final ServiceName A_ALIAS = ServiceName.of("A alias");
    private static final ServiceName B = ServiceName.of("B");
    private static final ServiceName B_ALIAS = ServiceName.of("B alias");
    private static final ServiceName C = ServiceName.of("C");
    private static final ServiceName C_ALIAS = ServiceName.of("C alias");
    private static final ServiceName D = ServiceName.of("D");
    private static final ServiceName E = ServiceName.of("E");
    private static final ServiceName F = ServiceName.of("F");
    private static final ServiceName G = ServiceName.of("G");
    private static final ServiceName H = ServiceName.of("H");
    private static final ServiceName I = ServiceName.of("I");
    private static final ServiceName J = ServiceName.of("J");
    private static final ServiceName K = ServiceName.of("K");
    private static final ServiceName L = ServiceName.of("L");

    @Test
    public void usecase0() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, A);
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[A, A]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase1() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, A);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[B, A, B]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase2() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C);
            addService(txn, C, A);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[C, A, B, C]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase3() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C, E);
            addService(txn, C, D);
            addService(txn, E, A);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[E, A, B, E]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase4() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C, E);
            addService(txn, C, D, G);
            addService(txn, E, F, H);
            addService(txn, F, A);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[F, A, B, E, F]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase5() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C, E);
            addService(txn, C, D);
            addService(txn, D, G, F);
            addService(txn, F, H);
            addService(txn, H, E);
            addService(txn, E, C);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[E, C, D, F, H, E]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase6() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C, E);
            addService(txn, C, D);
            addService(txn, D, G, F);
            addService(txn, F, H);
            addService(txn, H, E, A);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[H, A, B, C, D, F, H]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase7() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, A, B);
            addService(txn, B, C, E);
            addService(txn, C, D);
            addService(txn, D, G, F);
            addService(txn, F, H);
            addService(txn, H, E);
            addService(txn, G, I);
            addService(txn, I, L, J);
            addService(txn, J, K);
            addService(txn, E, C);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[E, C, D, F, H, E]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase8() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, B, new ServiceName[] {B_ALIAS}, C_ALIAS);
            addService(txn, C, new ServiceName[] {C_ALIAS}, A);
            addService(txn, A, B_ALIAS);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[A, \"B alias\", \"C alias\", A]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    @Test
    public void usecase9() {
        final UpdateTransaction txn = newUpdateTransaction();
        try {
            addService(txn, B, new ServiceName[] {B_ALIAS}, C_ALIAS);
            addService(txn, C, new ServiceName[] {C_ALIAS}, A_ALIAS);
            addService(txn, A, new ServiceName[] {A_ALIAS}, B_ALIAS);
            fail("Dependencies cycle expected");
        } catch (final CircularDependencyException e) {
            assertCycle(e, "[\"A alias\", \"B alias\", \"C alias\", \"A alias\"]");
        } finally {
            prepare(txn);
            commit(txn);
        }
    }

    private void assertCycle(final CircularDependencyException e, final String expectedCycle) {
        assertTrue(e.getMessage().indexOf(" service installation failed because it introduced the following cycle: " + expectedCycle) > 0);
    }

    private void addService(final UpdateTransaction txn, final ServiceName name, final ServiceName... dependencies) {
        addService(txn, name, null, dependencies);
    }

    private void addService(final UpdateTransaction txn, final ServiceName name, final ServiceName[] aliases, final ServiceName... dependencies) {
        ServiceBuilder sb = txnController.getServiceContext(txn).addService(serviceRegistry, name);
        sb.addAliases(aliases);
        for (int i = 0; i < dependencies.length; i++) {
            sb.addDependency(dependencies[i]);
        }
        sb.install();
    }

}
