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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;

/**
 * Lock-free cycle detection helper. There are two cycle detection algorithms
 * available, one is recursion based and second one is non-recursive. The
 * algorithm to be used can be configured on the commandline:
 * <PRE>
 *     -Djboss.msc.nonrecursive.cycle.detection=[true|false]
 * </PRE>
 * By default the recursion based algorithm is used.
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class CycleDetector {

    private static final CycleDetectionAlgorithm ALGORITHM;

    static {
        final boolean noRecursion = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            public Boolean run() {
                return Boolean.getBoolean("jboss.msc.nonrecursive.cycle.detection");
            }
        });
        ALGORITHM = noRecursion ? NonRecursiveAlgorithm.INSTANCE : RecursiveAlgorithm.INSTANCE;
    }

    private CycleDetector() {
        // forbidden instantiation
    }

    static void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException {
        ALGORITHM.execute(rootController);
    }

    private interface CycleDetectionAlgorithm {
        void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException;
    }

    private static final class RecursiveAlgorithm implements CycleDetectionAlgorithm {

        private static final RecursiveAlgorithm INSTANCE = new RecursiveAlgorithm();

        private RecursiveAlgorithm() {}

        public void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException {
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

    private static final class NonRecursiveAlgorithm implements CycleDetectionAlgorithm  {

        private static final NonRecursiveAlgorithm INSTANCE = new NonRecursiveAlgorithm();

        private NonRecursiveAlgorithm() {}

        /**
         * Cycle detection tracking information.
         */
        private static final class Branch {
            /** Edges to investigate since last branch point. */
            final Deque<Registration> stack = new ArrayDeque<>();
            /** Path we walked since last branch point. */
            final Deque<ServiceName> path = new ArrayDeque<>();
        }

        public void execute(final ServiceControllerImpl<?> rootController) throws CircularDependencyException {
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
            Branch currentBranch = new Branch();
            for (final DependencyImpl dependency : rootController.dependencies) {
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
                    } else if (dependencyController == rootController) {
                        // we returned to the root controller, we have the cycle!
                        throw SERVICE.cycleDetected(rootController.getPrimaryRegistration().getServiceName(), getCycle(branches));
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

}
