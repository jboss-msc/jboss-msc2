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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple transaction task that tracks task calls. It provides utility methods:
 * 
 * <UL>
 * <LI>{@link #isExecuted()} - returns <code>true</code> if transaction have been executed, <code>false</code> otherwise</LI>
 * </UL>
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class TrackingTask extends TestExecutable<Object> {

    private final AtomicBoolean executed = new AtomicBoolean();

    @Override
    protected void executeInternal(TestExecuteContext<Object> ctx) {
        executed.set(true);
    }

    public final boolean isExecuted() {
        return executed.get();
    }

}
