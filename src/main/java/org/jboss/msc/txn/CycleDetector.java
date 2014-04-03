/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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
package org.jboss.msc.txn;

import static org.jboss.msc._private.MSCLogger.SERVICE;

import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.ServiceName;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Set;

/**
 * Lock-free cycle detection helper.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class CycleDetector {

    private CycleDetector() {
        // forbidden instantiation
    }

    static void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException {
        if (rootController.dependencies.length == 0) return;

        final Deque<ServiceName> visitStack = new ArrayDeque<>();
        final Set<ServiceControllerImpl> visited = new IdentityHashSet<>();

        visited.add(rootController);
        detectCircularity(rootController.dependencies, rootController, visited, visitStack);
    }

    private static void detectCircularity(final DependencyImpl<?>[] dependencies, final ServiceControllerImpl<?> root, final Set<ServiceControllerImpl> visited, final Deque<ServiceName> visitStack) {
        ServiceControllerImpl dependencyController;
        for (final DependencyImpl<?> dependency : dependencies) {
            dependencyController = getController(dependency.getDependencyRegistration());
            if (dependencyController == null) continue;
            if (root == dependencyController) {
                // we returned to the root controller, we have the cycle!
                // We're pushing last element here to track and report alias based cycles.
                visitStack.push(dependency.getDependencyRegistration().getServiceName());
                throw SERVICE.cycleDetected(root.getPrimaryRegistration().getServiceName(), getCycle(visitStack));
            }
            if (visited.add(dependencyController)) {
                if (dependencyController.dependencies.length > 0) {
                    visitStack.push(dependency.getDependencyRegistration().getServiceName());
                    detectCircularity(dependencyController.dependencies, root, visited, visitStack);
                    visitStack.pop();
                }
            }
        }
    }

    /**
     * Gets controller associated with <B>registration</B> if and only if it is in <B>'cycle detection set'</B>, <code>null</code> otherwise.
     * @param registration registration
     * @return controller or null
     */
    private static ServiceControllerImpl getController(final Registration registration) {
        final ControllerHolder holder = registration.holderRef.get();
        return holder != null && holder.inCycleDetectionSet ? holder.controller : null;
    }

    /**
     * Creates cycle report. First and last element in the cycle are always identical.
     * @param path that lead to the cycle
     * @return cycle report
     */
    private static ArrayList<ServiceName> getCycle(final Deque<ServiceName> path) {
        final ArrayList<ServiceName> cycle = new ArrayList<>();
        cycle.add(path.peekFirst());
        while (!path.isEmpty()) {
            cycle.add(path.pollLast());
        }
        return cycle;
    }

}
