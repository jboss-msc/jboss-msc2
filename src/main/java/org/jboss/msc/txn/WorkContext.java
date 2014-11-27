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
package org.jboss.msc.txn;

import org.jboss.msc.problem.Problem;

/**
 * Service lifecycle context.
 *
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public interface WorkContext {

    /**
     * Marking service completion without value. This method returns without blocking.
     */
    void complete();

    void addProblem(Problem reason);

    void addProblem(Problem.Severity severity, String message);

    void addProblem(Problem.Severity severity, String message, Throwable cause);

    void addProblem(String message, Throwable cause);

    void addProblem(String message);

    void addProblem(Throwable cause);

}
