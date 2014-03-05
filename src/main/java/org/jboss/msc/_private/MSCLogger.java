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

package org.jboss.msc._private;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.INFO;

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.txn.Executable;
import org.jboss.msc.txn.InvalidTransactionStateException;
import org.jboss.msc.txn.Revertible;

/**
 * MSC2 logging utilities.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 */
@MessageLogger(projectCode = "MSC")
public interface MSCLogger {

    // **********************************************************
    // **********************************************************
    // **                                                      **
    // ** IMPORTANT - Be sure to check against the 1.x         **
    // **     codebase before assigning additional IDs         **
    // **     in this file!                                    **
    // **                                                      **
    // **********************************************************
    // **********************************************************

    MSCLogger ROOT = Logger.getMessageLogger(MSCLogger.class, "org.jboss.msc");
    MSCLogger SERVICE = Logger.getMessageLogger(MSCLogger.class, "org.jboss.msc.service");
    MSCLogger FAIL = Logger.getMessageLogger(MSCLogger.class, "org.jboss.msc.service.fail");
    MSCLogger TASK = Logger.getMessageLogger(MSCLogger.class, "org.jboss.msc.task");
    MSCLogger TXN = Logger.getMessageLogger(MSCLogger.class, "org.jboss.msc.txn");

    @LogMessage(level = INFO)
    @Message(value = "JBoss MSC version %s")
    void greeting(String version);

    @LogMessage(level = ERROR)
    @Message(id = 1, value = "Failed to start %s")
    void startFailed(ServiceName serviceName);

    @LogMessage(level = ERROR)
    @Message(id = 2, value = "Invocation of listener \"%s\" failed")
    void listenerFailed(@Cause Throwable cause, Object listener);

    // id = 3: exception after start completed (N/A)

    // id = 4: service stop failed (N/A)

    // id = 5: stop service missing (N/A)

    // id = 6: internal service error (N/A)

    // id = 8: unexpected worker thread exception (N/A)

    // id = 9: profile output file close error (N/A)

    // id = 10: failed mbean registration (N/A)

    @Message(id = 11, value = "Service not started")
    IllegalStateException serviceNotStarted();

    @LogMessage(level = ERROR)
    @Message(id = 12, value = "Execution of task \"%s\" caused an exception")
    void taskExecutionFailed(@Cause Throwable cause, Executable<?> task);

    @LogMessage(level = ERROR)
    @Message(id = 13, value = "Validation of task \"%s\" caused an exception")
    void taskValidationFailed(@Cause Throwable cause, Object task);

    @LogMessage(level = ERROR)
    @Message(id = 14, value = "Rollback of task \"%s\" caused an exception")
    void taskRollbackFailed(@Cause Throwable cause, Revertible task);

    @LogMessage(level = FATAL)
    @Message(id = 15, value = "Internal task \"%s\" execution failed (transaction is likely permanently jammed)")
    void runnableExecuteFailed(@Cause Throwable cause, Runnable command);

    @Message(id = 16, value ="Service %s has a required dependency on service %s that is missing")
    String requiredDependency(ServiceName dependentName, ServiceName dependencyName);

    @Message(id = 17, value="Dependency cycle found: %s")
    String dependencyCycle(ServiceName[] cycle);

    @LogMessage(level = FATAL)
    @Message(id = 18, value = "Lock cleanup failed")
    void lockCleanupFailed(@Cause Throwable cause);

    @LogMessage(level = FATAL)
    @Message(id = 19, value = "Transaction termination listener execution failed")
    void terminationListenerFailed(@Cause Throwable cause);

    @LogMessage(level = FATAL)
    @Message(id = 20, value = "Lock request listener execution failed")
    void lockListenerFailed(@Cause Throwable cause);

    // jump to 100...

    /*
     * This method is for uninjection failures which are not service-related.  See also id = 6
     */
    @LogMessage(level = Logger.Level.WARN)
    @Message(id = 100, value = "Unexpected failure to uninject %s")
    void uninjectFailed(@Cause Throwable cause, Object target);

    @Message(id = 101, value = "Parameter %s is null")
    IllegalArgumentException methodParameterIsNull(final String parameterName);

    @Message(id = 102, value = "%s must be at most ERROR")
    IllegalArgumentException illegalSeverity(final String parameterName);

    @Message(id = 103, value = "Too many active transactions")
    IllegalStateException tooManyActiveTransactions();

    @Message(id = 104, value = "%s and %s flags are mutually exclusive")
    IllegalStateException mutuallyExclusiveFlags(final String flag1, final String flag2);

    @Message(id = 105, value = "Parameter %s is invalid")
    IllegalArgumentException methodParameterIsInvalid(final String parameterName);

    @Message (id = 106, value = "ServiceRegistry is removed")
    IllegalStateException removedServiceRegistry();

    @Message (id = 107, value = "Transaction cannot prepare: rollback requested")
    InvalidTransactionStateException cannotPrepareRolledbackTxn();

    @Message (id = 108, value = "Transaction must be in active state to prepare")
    InvalidTransactionStateException cannotPrepareNonActiveTxn();

    @Message (id = 109, value = "Transaction cannot prepare: prepare already called")
    InvalidTransactionStateException cannotPreparePreparedTxn();

    @Message (id = 110, value = "Transaction cannot commit: rollback requested")
    InvalidTransactionStateException cannotCommitRolledbackTxn();

    @Message (id = 111, value = "Transaction must be in prepared state to commit ")
    InvalidTransactionStateException cannotCommitUnpreparedTxn();

    @Message (id = 112, value = "Transaction cannot commit: problem reported")
    InvalidTransactionStateException cannotCommitProblematicTxn();

    @Message (id = 113, value = "Transaction cannot commit: commit already called")
    InvalidTransactionStateException cannotCommitCommittedTxn();

    @Message (id = 114, value = "Transaction must be in prepared state to abort")
    InvalidTransactionStateException cannotAbortUnpreparedTxn();

    @Message (id = 115, value = "Transaction cannot abort: abort or rollback already called")
    InvalidTransactionStateException cannotAbortAbortedTxn();

    @Message (id = 116, value = "Transaction must not be in prepared state to rollback")
    InvalidTransactionStateException cannotRollbackPreparedTxn();

    @Message (id = 117, value = "Transaction cannot rollback: abort or rollback already called")
    InvalidTransactionStateException cannotRollbackRolledbackTxn();

    @Message (id = 118, value = "Transaction must be in prepared state to inspect commitable status")
    InvalidTransactionStateException cannotInspectUnpreparedTxn();

    @Message (id = 119, value = "Cannot create child task at this stage: transaction is no longer active (current state: %s)")
    InvalidTransactionStateException cannotAddChildToInactiveTxn(final int state);

    @Message (id = 120, value = "Cannot cancel child task at this stage: transaction is no longer active (current state: %s)")
    InvalidTransactionStateException cannotCancelChildOnInactiveTxn(final int state);

    @Message (id = 121, value = "Cannot use transaction at this stage: the transaction is terminated.")
    InvalidTransactionStateException txnTerminated();

    @Message (id = 122, value = "No result is available")
    IllegalStateException noTaskResult();

    @Message (id = 123, value = "Task may not be completed now")
    IllegalStateException taskCannotComplete();

    @Message (id = 124, value = "Task may not be cancelled now")
    IllegalStateException taskCannotCancel();

    @Message (id = 125, value = "Task may not be reverted now")
    IllegalStateException taskCannotRollback();

    @Message (id = 126, value = "Dependent may not be added at this point")
    IllegalStateException cannotAddDepToTask();

    @Message (id = 127, value = "A service named %s is already installed")
    DuplicateServiceException duplicateService(final ServiceName serviceName);

    @Message (id = 128, value = "Cannot add new tasks to inactive transaction")
    InvalidTransactionStateException inactiveTransaction();

    @Message (id = 129, value = "Cannot add new tasks with revertible component to inactive transaction")
    InvalidTransactionStateException cannotAddRevertibleToInactiveTransaction();

    @Message (id = 130, value = "It is forbidden to create dependency on registry created by other transaction controller")
    IllegalArgumentException cannotCreateDependencyOnRegistryCreatedByOtherTransactionController();

    @Message (id = 131, value = "Transaction controller mismatch.")
    IllegalArgumentException transactionControllerMismatch();

    /*
     * Location nesting types.
     */

    @Message(value = "at")
    String nestingUnknown();

    @Message(value = "contained in")
    String nestingContained();

    @Message(value = "included from")
    String nestingIncluded();

    @Message(value = "defined in")
    String nestingDefined();
}
