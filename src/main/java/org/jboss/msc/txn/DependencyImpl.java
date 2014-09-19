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

package org.jboss.msc.txn;

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.Dependency;
import org.jboss.msc.service.DependencyFlag;
import org.jboss.msc.txn.Problem.Severity;

import static org.jboss.msc._private.MSCLogger.SERVICE;
import static org.jboss.msc.txn.Helper.getAbstractTransaction;

/**
 * Dependency implementation.
 * 
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 *
 * @param <T>
 */
class DependencyImpl<T> implements Dependency<T> {

    private static final byte REQUIRED_FLAG   = (byte)(1 << DependencyFlag.REQUIRED.ordinal());
    private static final byte UNREQUIRED_FLAG = (byte)(1 << DependencyFlag.UNREQUIRED.ordinal());
    private static final byte DEMANDED_FLAG   = (byte)(1 << DependencyFlag.DEMANDED.ordinal());
    private static final byte UNDEMANDED_FLAG = (byte)(1 << DependencyFlag.UNDEMANDED.ordinal());

    /**
     * Dependency flags.
     */
    private final byte flags;
    /**
     * The dependency registration.
     */
    private final Registration dependencyRegistration;
    /**
     * Indicates if the dependency should be demanded to be satisfied when service is attempting to start.
     */
    private final boolean propagateDemand;
    /**
     * The incoming dependency.
     */
    protected ServiceControllerImpl<?> dependent;

    /**
     * Creates a simple dependency to {@code dependencyRegistration}.
     * 
     * @param dependencyRegistration the dependency registration
     * @param flags dependency flags
     */
    protected DependencyImpl(final Registration dependencyRegistration, final DependencyFlag... flags) {
        byte translatedFlags = 0;
        for (final DependencyFlag flag : flags) {
            if (flag != null) {
                translatedFlags |= (1 << flag.ordinal());
            }
        }
        if (Bits.allAreSet(translatedFlags, UNDEMANDED_FLAG | DEMANDED_FLAG)) {
            throw SERVICE.mutuallyExclusiveFlags(DependencyFlag.DEMANDED.toString(), DependencyFlag.UNDEMANDED.toString());
        }
        if (Bits.allAreSet(translatedFlags, REQUIRED_FLAG | UNREQUIRED_FLAG)) {
            throw SERVICE.mutuallyExclusiveFlags(DependencyFlag.REQUIRED.toString(), DependencyFlag.UNREQUIRED.toString());
        }
        this.flags = translatedFlags;
        this.dependencyRegistration = dependencyRegistration;
        this.propagateDemand = !hasDemandedFlag() && !hasUndemandedFlag();
    }

    final boolean hasRequiredFlag() {
        return Bits.allAreSet(flags, REQUIRED_FLAG);
    }

    final boolean hasUnrequiredFlag() {
        return Bits.allAreSet(flags, UNREQUIRED_FLAG);
    }

    final boolean hasDemandedFlag() {
        return Bits.allAreSet(flags, DEMANDED_FLAG);
    }

    final boolean hasUndemandedFlag() {
        return Bits.allAreSet(flags, UNDEMANDED_FLAG);
    }

    public T get() {
        @SuppressWarnings("unchecked")
        ServiceControllerImpl<T> dependencyController = (ServiceControllerImpl<T>) dependencyRegistration.getController();
        return dependencyController == null? null: dependencyController.getValue();
    }

    /**
     * Sets the dependency dependent, invoked during {@code dependentController} installation or {@link ParentDependency}
     * activation (when parent dependency is satisfied and installed).
     * 
     * @param dependent    dependent associated with this dependency
     * @param transaction  the active transaction
     */
    void setDependent(ServiceControllerImpl<?> dependent, Transaction transaction) {
        setDependent(dependent, transaction, getAbstractTransaction(transaction).getTaskFactory());
    }

    private void setDependent(ServiceControllerImpl<?> dependent, Transaction transaction, TaskFactory taskFactory) {
        synchronized (this) {
            this.dependent = dependent;
            dependencyRegistration.addIncomingDependency(transaction, this);
            if (!propagateDemand && hasDemandedFlag()) {
                dependencyRegistration.addDemand(transaction, taskFactory);
            }
        }
    }

    /**
     * Clears the dependency dependent, invoked during {@code dependentController} removal.
     * 
     * @param transaction   the active transaction
     * @param taskFactory   the task factory
     */
    void clearDependent(Transaction transaction, TaskFactory taskFactory) {
        dependencyRegistration.removeIncomingDependency(this);
        if (!propagateDemand && hasDemandedFlag()) {
            dependencyRegistration.removeDemand(transaction, taskFactory);
        }
    }

    /**
     * Returns the dependency registration.
     * 
     * @return the dependency registration
     */
    Registration getDependencyRegistration() {
        return dependencyRegistration;
    }

    /**
     * Demands this dependency to be satisfied.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void demand(Transaction transaction, TaskFactory taskFactory) {
        if (propagateDemand) {
            dependencyRegistration.addDemand(transaction, taskFactory);
        }
    }

    /**
     * Removes demand for this dependency to be satisfied.
     * 
     * @param transaction the active transaction
     * @param taskFactory the task factory
     */
    void undemand(Transaction transaction, TaskFactory taskFactory) {
        if (propagateDemand) {
            dependencyRegistration.removeDemand(transaction, taskFactory);
        }
    }

    /**
     * Notifies that dependency is now {@code UP} or is scheduled to start.
     * 
     * @param transaction   the active transaction
     */
    void dependencyUp(final Transaction transaction) {
        dependent.dependencySatisfied(transaction);
    }

    /**
     * Notifies that dependency is now stopping.
     *  
     * @param transaction    the active transaction
     * @param taskFactory    the task factory
     */
    void dependencyDown(Transaction transaction, TaskFactory taskFactory) {
        dependent.dependencyUnsatisfied(transaction, taskFactory);
    }

    /**
     * Validates dependency state before active transaction commits.
     * 
     * @param report report where all validation problems found will be added
     */
    void validate(final ProblemReport report) {
        final ServiceControllerImpl<?> controller = dependencyRegistration.holderRef.get();
        if (controller == null && !hasUnrequiredFlag()) {
            report.addProblem(new Problem(Severity.ERROR, MSCLogger.SERVICE.requiredDependency(dependent.getServiceName(), dependencyRegistration.getServiceName())));
        }
    }

}