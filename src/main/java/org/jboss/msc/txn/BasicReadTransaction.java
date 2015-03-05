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

import java.util.concurrent.Executor;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class BasicReadTransaction<T extends ReadTransaction<T>> extends AbstractTransaction implements ReadTransaction<T> {

    BasicReadTransaction(final TransactionController controller, final Executor taskExecutor) {
        super(controller, taskExecutor);
        setWrappingTransaction(this);
    }

    public final <T extends UpdateTransaction> void addPostPrepare(final Action<T> completionListener) {
        super.addPostPrepareListener(completionListener);
    }

    public final <T extends UpdateTransaction> void removePostPrepare(final Action<T> completionListener) {
        super.removePostPrepareListener(completionListener);
    }

    public final <T extends UpdateTransaction> void addPostRestart(final Action<T> completionListener) {
        super.addPostRestartListener(completionListener);
    }

    public final <T extends UpdateTransaction> void removePostRestart(final Action<T> completionListener) {
        super.removePostRestartListener(completionListener);
    }

    @Override
    public final void addPostCommit(final Action<T> completionListener) {
        super.addPostCommitListener(completionListener);
    }

    @Override
    public final void removePostCommit(final Action<T> completionListener) {
        super.removePostCommitListener(completionListener);
    }

}

