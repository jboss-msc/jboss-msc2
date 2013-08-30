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

import com.arjuna.ats.arjuna.common.Uid;
import com.arjuna.ats.arjuna.coordinator.AbstractRecord;
import com.arjuna.ats.arjuna.coordinator.RecordType;
import com.arjuna.ats.arjuna.coordinator.TwoPhaseOutcome;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class ArjunaRecordImplementation extends AbstractRecord {

    private final ArjunaResource arjunaResource;
    private final Transaction transaction;

    private volatile Object value;

    ArjunaRecordImplementation(final ArjunaResource arjunaResource, final Transaction transaction) {
        super(new Uid());
        this.arjunaResource = arjunaResource;
        this.transaction = transaction;
    }

    ArjunaResource getArjunaResource() {
        return arjunaResource;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public int typeIs() {
        return RecordType.USER_DEF_FIRST0;
    }

    public Object value() {
        return value;
    }

    public void setValue(final Object value) {
        this.value = value;
    }

    public int nestedAbort() {
        return TwoPhaseOutcome.FINISH_OK;
    }

    public int nestedCommit() {
        return TwoPhaseOutcome.FINISH_ERROR;
    }

    public int nestedPrepare() {
        return TwoPhaseOutcome.PREPARE_NOTOK;
    }

    public int topLevelAbort() {
        try {
            final SynchronousRollbackListener<Transaction> listener = new SynchronousRollbackListener<>();
            transaction.rollback(listener);
            final RollbackResult<Transaction> rollbackResult = listener.awaitUninterruptibly();
            return rollbackResult.isRolledBack() ? TwoPhaseOutcome.FINISH_OK : TwoPhaseOutcome.FINISH_ERROR;
        } catch (final TransactionRolledBackException e) {
            return TwoPhaseOutcome.FINISH_ERROR;
        } catch (final InvalidTransactionStateException e) {
            return TwoPhaseOutcome.FINISH_ERROR;
        }
    }

    public int topLevelCommit() {
        try {
            final SynchronousCommitListener<Transaction> listener = new SynchronousCommitListener<>();
            transaction.commit(listener);
            final CommitResult<Transaction> commitResult = listener.awaitUninterruptibly();
            return commitResult.isCommitted() ? TwoPhaseOutcome.FINISH_OK : TwoPhaseOutcome.FINISH_ERROR;
        } catch (final TransactionRolledBackException e) {
            return TwoPhaseOutcome.FINISH_ERROR;
        } catch (final InvalidTransactionStateException e) {
            return TwoPhaseOutcome.FINISH_ERROR;
        }
    }

    public int topLevelPrepare() {
        try {
            final SynchronousPrepareListener<Transaction> listener = new SynchronousPrepareListener<>();
            transaction.prepare(listener);
            final PrepareResult<Transaction> prepareResult = listener.awaitUninterruptibly();
            return prepareResult.isPrepared() ? TwoPhaseOutcome.PREPARE_OK : TwoPhaseOutcome.PREPARE_NOTOK;
        } catch (final TransactionRolledBackException e) {
            return TwoPhaseOutcome.PREPARE_NOTOK;
        } catch (final InvalidTransactionStateException e) {
            return TwoPhaseOutcome.PREPARE_NOTOK;
        }
    }

    public void merge(final AbstractRecord a) {
    }

    public void alter(final AbstractRecord a) {
    }

    public boolean shouldAdd(final AbstractRecord a) {
        return false;
    }

    public boolean shouldAlter(final AbstractRecord a) {
        return false;
    }

    public boolean shouldMerge(final AbstractRecord a) {
        return false;
    }

    public boolean shouldReplace(final AbstractRecord a) {
        return false;
    }

    public String type() {
        return super.type() + "/MSC";
    }
}
