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

package org.jboss.msc.txn;

/**
 * Task builder.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
interface TaskBuilder<T> {

    /**
     * Get the transaction associated with this builder.
     *
     * @return the transaction associated with this builder
     */
    Transaction getTransaction();

    /**
     * Set the class loader to use for this task.
     *
     * @param classLoader the class loader
     * @return this task builder
     */
    TaskBuilder<T> setClassLoader(final ClassLoader classLoader);

    /**
     * Release this task to begin execution.
     */
    void release();

}
