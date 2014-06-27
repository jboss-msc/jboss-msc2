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

import org.jboss.msc.service.ServiceContainer;

/**
 * A task that shuts down the container.
 * 
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class ShutdownContainerTask implements Executable<Void> {

    private final ServiceContainer container;
    private final UpdateTransaction transaction;

    public ShutdownContainerTask(final ServiceContainer container, final UpdateTransaction transaction) {
        this.container = container;
        this.transaction = transaction;
    }

    @Override
    public void execute(final ExecuteContext<Void> context) {
        try {
            container.shutdown(transaction);
        } finally {
            context.complete();
        }
    }

}
