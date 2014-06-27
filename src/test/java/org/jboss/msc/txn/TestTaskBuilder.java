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

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class TestTaskBuilder<T> {

    private final TaskBuilder<T> delegate;

    TestTaskBuilder(final TaskBuilder<T> delegate) {
        this.delegate = delegate;
    }

    public TestTaskController<T> release() {
        return new TestTaskController(delegate.release());
    }

    public TestTaskBuilder<T> addDependency(final TestTaskController<?> dependency) {
        delegate.addDependency(dependency.getDelegate());
        return this;
    }

    public TestTaskBuilder<T> addDependencies(final TestTaskController<?>... dependencies) {
        final TaskController[] deps = new TaskController[dependencies.length];
        for (int i = 0; i < dependencies.length; i++) deps[i] = dependencies[i].getDelegate();
        delegate.addDependencies(deps);
        return this;
    }
}
