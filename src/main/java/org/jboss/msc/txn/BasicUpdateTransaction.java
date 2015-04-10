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
import org.jboss.msc.problem.ProblemReport;
import org.jboss.msc.util.AttachmentKey;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
final class BasicUpdateTransaction implements UpdateTransaction {

    private final BasicReadTransaction delegate;
    private boolean updated;
    private volatile boolean invalidated;

    BasicUpdateTransaction(final BasicReadTransaction delegate) {
        this.delegate = delegate;
        delegate.setWrappingTransaction(this);
    }

    void setModified() throws InvalidTransactionStateException {
        synchronized (delegate.getLock()) {
            if (invalidated) throw MSCLogger.TXN.invalidatedUpdateTransaction();
            updated = true;
        }
    }

    boolean isModified() {
        synchronized (delegate.getLock()) {
            return updated;
        }
    }

    void invalidate() {
        synchronized (delegate.getLock()) {
            invalidated = true;
        }
    }

    final Object getLock() {
        return delegate.getLock();
    }

    private void assertState() {
        if (invalidated) throw MSCLogger.TXN.invalidatedUpdateTransaction();
    }

    TransactionController getController() {
        assertState();
        return delegate.txnController;
    }

    BasicReadTransaction getDelegate() {
        assertState();
        return delegate;
    }

    @Override
    public long getDuration(final TimeUnit unit) {
        assertState();
        return delegate.getDuration(unit);
    }

    @Override
    public ProblemReport getReport() {
        assertState();
        return delegate.getReport();
    }

    @Override
    public boolean isPrepared() {
        assertState();
        return delegate.isPrepared();
    }

    @Override
    public boolean isTerminated() {
        assertState();
        return delegate.isTerminated();
    }

    @Override
    public Executor getExecutor() {
        assertState();
        return delegate.getExecutor();
    }

    @Override
    public <T> T getAttachment(final AttachmentKey<T> key) {
        assertState();
        return delegate.getAttachment(key);
    }

    @Override
    public boolean hasAttachment(final AttachmentKey<?> key) {
        assertState();
        return delegate.hasAttachment(key);
    }

    @Override
    public <T> T putAttachment(final AttachmentKey<T> key, final T newValue) {
        assertState();
        return delegate.putAttachment(key, newValue);
    }

    @Override
    public <T> T putAttachmentIfAbsent(final AttachmentKey<T> key, final T newValue) {
        assertState();
        return delegate.putAttachmentIfAbsent(key, newValue);
    }

    @Override
    public <T> T removeAttachment(final AttachmentKey<T> key) {
        assertState();
        return delegate.removeAttachment(key);
    }

    @Override
    public <T> boolean removeAttachment(final AttachmentKey<T> key, final T expectedValue) {
        assertState();
        return delegate.removeAttachment(key, expectedValue);
    }

    @Override
    public <T> T replaceAttachment(final AttachmentKey<T> key, final T newValue) {
        assertState();
        return delegate.replaceAttachment(key, newValue);
    }

    @Override
    public <T> boolean replaceAttachment(final AttachmentKey<T> key, final T expectedValue, final T newValue) {
        assertState();
        return delegate.replaceAttachment(key, expectedValue, newValue);
    }

    @Override
    public <T> boolean ensureAttachmentValue(final AttachmentKey<T> key, final T expectedValue) {
        assertState();
        return delegate.ensureAttachmentValue(key, expectedValue);
    }

    @Override
    public final void addPostPrepare(final Action completionListener) {
        assertState();
        delegate.addPostPrepare(completionListener);
    }

    @Override
    public final void removePostPrepare(final Action completionListener) {
        assertState();
        delegate.removePostPrepare(completionListener);
    }

    @Override
    public final void addPostRestart(final Action completionListener) {
        assertState();
        delegate.addPostRestart(completionListener);
    }

    @Override
    public final void removePostRestart(final Action completionListener) {
        assertState();
        delegate.removePostRestart(completionListener);
    }

    @Override
    public final void addPostCommit(final Action completionListener) {
        assertState();
        delegate.addPostCommit(completionListener);
    }

    @Override
    public final void removePostCommit(final Action completionListener) {
        assertState();
        delegate.removePostCommit(completionListener);
    }

    @Override
    public final TransactionHoldHandle acquireHoldHandle() {
        assertState();
        return delegate.acquireHoldHandle();
    }

}
    