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

import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;
import org.jboss.msc.service.CircularDependencyException;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.txn.InvalidTransactionStateException;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.FATAL;
import static org.jboss.logging.Logger.Level.INFO;

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

    @LogMessage(level = ERROR)
    @Message(id = 11, value = "Execution of task \"%s\" caused an exception")
    void taskExecutionFailed(@Cause Throwable cause, Object task);

    @LogMessage(level = FATAL)
    @Message(id = 12, value = "Internal task \"%s\" execution failed (transaction is likely permanently jammed)")
    void runnableExecuteFailed(@Cause Throwable cause, Runnable command);

    @Message(id = 13, value ="Service %s has a required dependency on service %s that is missing")
    String requiredDependency(ServiceName dependentName, ServiceName dependencyName);

    @LogMessage(level = FATAL)
    @Message(id = 14, value = "Transaction prepare completion listener failed")
    void prepareCompletionListenerFailed(@Cause Throwable cause);

    @LogMessage(level = FATAL)
    @Message(id = 15, value = "Transaction create completion listener failed")
    void transactionCreationCompletionListenerFailed(@Cause Throwable cause);

    // jump to 100...

    @Message(id = 101, value = "Parameter %s is null")
    IllegalArgumentException methodParameterIsNull(final String parameterName);

    @Message(id = 102, value = "%s and %s flags are mutually exclusive")
    IllegalStateException mutuallyExclusiveFlags(final String flag1, final String flag2);

    @Message(id = 103, value = "Parameter %s is invalid")
    IllegalArgumentException methodParameterIsInvalid(final String parameterName);

    @Message(id = 104, value = "ServiceRegistry is removed")
    IllegalStateException removedServiceRegistry();

    @Message(id = 105, value = "Transaction must be in active state to prepare")
    InvalidTransactionStateException cannotPrepareNonActiveTxn();

    @Message(id = 106, value = "Transaction cannot prepare: prepare already called")
    InvalidTransactionStateException cannotPreparePreparedTxn();

    @Message(id = 107, value = "Transaction must be in prepared state to commit ")
    InvalidTransactionStateException cannotCommitUnpreparedTxn();

    @Message(id = 108, value = "Transaction cannot commit: commit already called")
    InvalidTransactionStateException cannotCommitCommittedTxn();

    @Message(id = 109, value = "Transaction must be in prepared state to inspect commitable status")
    InvalidTransactionStateException cannotInspectUnpreparedTxn();

    @Message(id = 110, value = "Cannot create child task at this stage: transaction is no longer active (current state: %s)")
    InvalidTransactionStateException cannotAddChildToInactiveTxn(final int state);

    @Message(id = 111, value = "No result is available")
    IllegalStateException noTaskResult();

    @Message(id = 112, value = "Task may not be completed now")
    IllegalStateException taskCannotComplete();

    @Message(id = 113, value = "A service named %s is already installed")
    DuplicateServiceException duplicateService(final ServiceName serviceName);

    @Message(id = 114, value = "Cannot add new tasks to inactive transaction")
    InvalidTransactionStateException inactiveTransaction();

    @Message(id = 115, value = "It is forbidden to create dependency on registry created by other transaction controller")
    IllegalArgumentException cannotCreateDependencyOnRegistryCreatedByOtherTransactionController();

    @Message(id = 116, value = "Transaction controller mismatch.")
    IllegalArgumentException transactionControllerMismatch();

    @Message(id = 117, value = "ServiceBuilder.install() have been already called")
    IllegalStateException cannotCallInstallTwice();

    @Message(id = 118, value = "%s service installation failed because it introduced the following cycle: %s")
    CircularDependencyException cycleDetected(final Object name, final Object cycleReport);

    @Message(id = 119, value = "Transaction was downgraded to read-only transaction")
    InvalidTransactionStateException invalidatedUpdateTransaction();

    @Message(id = 120, value = "Illegal transaction argument")
    IllegalArgumentException illegalTransaction();

    @Message(id = 121, value = "Transaction can be restarted only once")
    InvalidTransactionStateException cannotRestartRestartedTxn();

    @Message(id = 122, value = "Cannot restart unprepared transaction")
    InvalidTransactionStateException cannotRestartUnpreparedTxn();

    @Message(id = 123, value = "Lifecycle context is no longer valid")
    IllegalStateException lifecycleContextNotValid();

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
