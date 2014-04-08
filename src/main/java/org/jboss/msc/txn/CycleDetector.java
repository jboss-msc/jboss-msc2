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
import java.util.Deque;
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
        final Deque<Registration> stack;
        /** Path we walked since last branch point. */
        final Deque<ServiceName> path;

        private Branch(final int stackSize) {
            stack = new ArrayDeque<>(stackSize);
            path = new ArrayDeque<>();
        }
    }

    static void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException {
        if (rootController.dependencies.length == 0) {
            // if controller has no dependencies, it cannot participate in any cycle
            return;
        }

        // branches queue - we're adding new branch here every time we'll identify next branch on the path
        final Deque<Branch> branches = new ArrayDeque<>();
        // identity based set of controllers we have visited so far
        final Set<ServiceControllerImpl> visited = new IdentityHashSet<>();

        // put root controller to visited set
        visited.add(rootController);
        Branch currentBranch = new Branch(rootController.dependencies.length);
        for (final DependencyImpl dependency : rootController.dependencies) {
            // register edges to investigate from root
            currentBranch.stack.addFirst(dependency.getDependencyRegistration());
        }
        branches.addFirst(currentBranch);

        Registration dependency;
        ServiceControllerImpl dependencyController;
        while (true) {
            dependency = currentBranch.stack.removeFirst();
            dependencyController = getController(dependency);
            if (dependencyController != null) {
                // current controller is in the 'cycle detection set', investigate its dependencies
                currentBranch.path.addLast(dependency.getServiceName()); // add current step to the path
                if (visited.add(dependencyController)) {
                    // we didn't visit this controller yet, our voyage continues
                    final DependencyImpl[] dependencies = dependencyController.dependencies;
                    if (dependencies.length > 1) {
                        // identified new branch on current path
                        currentBranch = new Branch(dependencies.length);
                        branches.addFirst(currentBranch);
                    }
                    for (final DependencyImpl d : dependencies) {
                        // register edges to investigate from current controller
                        currentBranch.stack.addFirst(d.getDependencyRegistration());
                    }
                    if (dependencies.length > 0) continue; // we didn't reach dead end - investigation continues
                } else if (dependencyController == rootController) {
                    // we returned to the root controller, we have the cycle!
                    throw SERVICE.cycleDetected(rootController.getPrimaryRegistration().getServiceName(), getCycle(branches));
                }
            }
            // investigation path dead end
            currentBranch.path.clear(); // cleanup path since last branch point
            while (currentBranch.stack.size() == 0) {
                // we're finished with this branch investigation - cleanup and return to the last branch we didn't investigate completely yet
                branches.pollFirst();
                currentBranch = branches.peekFirst();
                if (currentBranch == null) return; // we're done
                currentBranch.path.clear(); // always cleanup last path on unfinished branch that lead us to the previous dead end
            }
        }
    }

    /**
     * Creates cycle report. First and last element in the cycle are always identical.
     * @param branches cycle inspection data
     * @return cycle report
     */
    private static LinkedList<ServiceName> getCycle(final Deque<Branch> branches) {
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

    /**
     * Gets controller associated with <B>registration</B> if and only if it is in <B>'cycle detection set'</B>, <code>null</code> otherwise.
     * @param registration registration
     * @return controller or null
     */
    private static ServiceControllerImpl getController(final Registration registration) {
        final ServiceControllerImpl serviceController = registration.holderRef.get();
        // ignore every service that is not down/new or that has no dependencies
        if (serviceController != null && (serviceController.getDependencies().length == 0 ||
                serviceController.getNonSyncTxnState() > ServiceControllerImpl.STATE_DOWN)) {
            return null;
        }
        return serviceController;
    }

}
