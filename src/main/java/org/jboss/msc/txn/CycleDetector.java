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

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Set;

/**
 * Non-recursive lock-free cycle detection helper.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class CycleDetector {

    private CycleDetector() {
        // forbidden instantiation
    }

    /**
     * Cycle detection tracking information.
     */
    private static final class Branch {
        /** Edges to investigate since last branch point. */
        final Deque<Registration> stack = new LinkedList<>();
        /** Path we walked since last branch point. */
        final Deque<ServiceName> path = new LinkedList<>();
    }

    static void execute(final ServiceControllerImpl<?> controller) throws CircularDependencyException {
        if (controller.dependencies.length == 0) {
            // if controller has no dependencies, it cannot participate in any cycle
            return;
        }

        // branches queue - we're adding new branch here every time we'll identify next branch on the path
        final Deque<Branch> branches = new LinkedList<>();
        // identity based set of controllers we have visited so far
        final Set<ServiceControllerImpl> visited = Collections.newSetFromMap(new IdentityHashMap<ServiceControllerImpl, Boolean>());

        // put root controller to visited set
        visited.add(controller);
        Branch currentBranch = new Branch();
        for (final DependencyImpl dependency : controller.dependencies) {
            // register edges to investigate from root
            currentBranch.stack.push(dependency.getDependencyRegistration());
        }
        branches.push(currentBranch);

        Registration dependency;
        ServiceControllerImpl dependencyController;
        while (currentBranch != null) {
            dependency = currentBranch.stack.poll();
            dependencyController = getController(dependency);
            if (dependencyController != null) {
                // current controller is in the 'cycle detection set', investigate its dependencies
                currentBranch.path.add(dependency.getServiceName()); // add current step to the path
                if (visited.add(dependencyController)) {
                    // we didn't visit this controller yet, our voyage continues
                    final DependencyImpl[] dependencies = dependencyController.dependencies;
                    if (dependencies.length > 1) {
                        // identified new branch on current path
                        currentBranch = new Branch();
                        branches.push(currentBranch);
                    }
                    for (final DependencyImpl d : dependencies) {
                        // register edges to investigate from current controller
                        currentBranch.stack.push(d.getDependencyRegistration());
                    }
                    if (dependencies.length > 0) continue; // we didn't reach dead end - investigation continues
                } else {
                    // we already visited this controller, we have the cycle!
                    throw SERVICE.cycleDetected(controller.getPrimaryRegistration().getServiceName(), getCycle(branches));
                }
            }
            // investigation path dead end
            currentBranch.path.clear(); // cleanup path since last branch point
            if (currentBranch.stack.size() == 0) {
                // we're finished with this branch investigation - cleanup and return to the last branch we didn't investigate completely yet
                branches.poll();
                currentBranch = branches.peek();
                if (currentBranch != null) {
                    currentBranch.path.clear(); // always cleanup last path on unfinished branch that lead us to the previous dead end
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
     * @param branches cycle inspection data
     * @return cycle report
     */
    private static Collection<ServiceName> getCycle(final Deque<Branch> branches) {
        final LinkedList<ServiceName> cycle = new LinkedList<>();
        Branch currentBranch = branches.pollLast();
        while (currentBranch != null) {
            cycle.addAll(currentBranch.path);
            currentBranch.path.clear(); // help GC
            currentBranch.stack.clear(); // help GC
            currentBranch = branches.pollLast();
        }
        cycle.addFirst(cycle.getLast());
        return cycle;
    }

}
