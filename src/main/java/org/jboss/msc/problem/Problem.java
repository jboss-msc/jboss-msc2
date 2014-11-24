/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
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

package org.jboss.msc.problem;

import java.io.Serializable;

/**
 * A description of a subtask execution failure.  Subtask failures should be described without exceptions whenever
 * possible, and should always include a clear and complete message. If severity is not provided then it will default to ERROR.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class Problem implements Serializable {

    private static final long serialVersionUID = 7378993289655554246L;

    private final Severity severity;
    private final String message;
    private final Throwable cause;
    private final Location location;

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param message the error description
     * @param cause the optional exception cause
     * @param location the location description of the problem
     */
    public Problem(final Severity severity, final String message, final Throwable cause, final Location location) {
        this.severity = severity != null ? severity : Severity.ERROR;
        this.message = message;
        this.cause = cause;
        this.location = location;
    }

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param message the error description
     * @param cause the optional exception cause
     */
    public Problem(final Severity severity, final String message, final Throwable cause) {
        this(severity, message, cause, null);
    }

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param cause the optional exception cause
     * @param location the location description of the problem
     */
    public Problem(final Severity severity, final Throwable cause, final Location location) {
        this(severity, null, cause, location);
    }

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param cause the optional exception cause
     */
    public Problem(final Severity severity, final Throwable cause) {
        this(severity, null, cause, null);
    }

    /**
     * Construct a new instance.
     *
     * @param message the error description
     * @param cause the optional exception cause
     */
    public Problem(final String message, final Throwable cause) {
        this(Severity.ERROR, message, cause, null);
    }

    /**
     * Construct a new instance.
     *
     * @param message the error description
     * @param cause the optional exception cause
     * @param location the location description of the problem
     */
    public Problem(final String message, final Throwable cause, final Location location) {
        this(Severity.ERROR, message, cause, location);
    }

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param message the error description
     */
    public Problem(final Severity severity, final String message) {
        this(severity, message, null, null);
    }

    /**
     * Construct a new instance.
     *
     * @param severity the severity of the problem
     * @param message the error description
     * @param location the location description of the problem
     */
    public Problem(final Severity severity, final String message, final Location location) {
        this(severity, message, null, location);
    }

    /**
     * Construct a new instance.
     *
     * @param message the error description
     */
    public Problem(final String message) {
        this(Severity.ERROR, message, null, null);
    }

    /**
     * Construct a new instance.
     *
     * @param message the error description
     * @param location the location description of the problem
     */
    public Problem(final String message, final Location location) {
        this(Severity.ERROR, message, null, location);
    }

    /**
     * Construct a new instance.  The exception must not be {@code null}.
     *
     * @param cause the exception cause
     */
    public Problem(final Throwable cause) {
        this(Severity.ERROR, null, cause, null);
    }

    /**
     * Construct a new instance.  The exception must not be {@code null}.
     *
     * @param cause the exception cause
     * @param location the location description of the problem
     */
    public Problem(final Throwable cause, final Location location) {
        this(Severity.ERROR, null, cause, location);
    }

    /**
     * Get the description of the failure.  Will not be {@code null}.
     *
     * @return the description of the failure
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the exception cause.  May be {@code null} if an exception wasn't the cause of failure or if the exception
     * is unimportant.
     *
     * @return the exception, or {@code null}
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * Get the severity of this problem.
     *
     * @return the severity of this problem
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Get the source location of the problem, if any.
     *
     * @return the source location, or {@code null} if there is none
     */
    public Location getLocation() {
        return location;
    }

    /**
     * The severity of a problem.
     */
    public enum Severity {
        /**
         * This problem will not cause adverse effects, but it is something the user should be made aware of.
         * The transaction <B>DON'T NEED TO</B> be rolled back in this case.
         * Examples include deprecation or indication of a possible configuration problem.
         */
        INFO,
        /**
         * This problem could possibly cause adverse effects now or in the future.
         * The transaction <B>SHOULD</B> be rolled back in this case.
         */
        WARNING,
        /**
         * This problem will likely cause adverse effects now or in the future.
         * The transaction <B>HAVE TO</B> be rolled back in this case.
         * Examples include a failed service or a missing service dependency.
         */
        ERROR,
        /**
         * This problem will cause irreparable damage to the integrity of the transaction.
         * The transaction <B>HAVE TO</B> be rolled back in this case.
         * Examples include resource exhaustion.
         */
        CRITICAL,
        ;

        /**
         * Determine if this severity is contained in the given list.
         *
         * @param severities the list
         * @return {@code true} if this is found, {@code false} otherwise
         */
        public boolean in(final Severity... severities) {
            if (severities != null) {
                for (final Severity severity : severities) {
                    if (this == severity) return true;
                }
            }
            return false;
        }
    }
}
