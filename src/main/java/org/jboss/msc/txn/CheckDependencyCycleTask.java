/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jboss.msc._private.MSCLogger;
import org.jboss.msc.service.ServiceName;

/**
 * Task that checks for dependency cycles.
 * 
 * @author <a href="mailto:frainone@redhat.com">Flavia Rainone</a>
 *
 */
final class CheckDependencyCycleTask implements Validatable {

    static final AttachmentKey<CheckDependencyCycleTask> key = AttachmentKey.create();

    /**
     * Schedule a check for dependency cycles involving {@code service}. The check is performed during transaction
     * validation.
     * 
     * @param service     the service to be verified
     * @param transaction the active transaction
     */
    static void checkDependencyCycle(ServiceControllerImpl<?> service, Transaction transaction) {
        final CheckDependencyCycleTask task;
        if (transaction.hasAttachment(key)) {
            task = transaction.getAttachment(key);
        } else {
            task = new CheckDependencyCycleTask(transaction);
            transaction.getTaskFactory().newTask().setValidatable(task).release();
        }
        task.checkService(service);
    }

    private final List<ServiceControllerImpl<?>> services;
    private final Transaction transaction;

    private CheckDependencyCycleTask(Transaction transaction) {
        services = new CopyOnWriteArrayList<ServiceControllerImpl<?>>();
        this.transaction = transaction;
    }

    private void checkService(ServiceControllerImpl<?> service) {
        services.add(service);
    }

    @Override
    public void validate(ValidateContext context) {
        try {
            final Set<ServiceControllerImpl<?>> checkedServices = new HashSet<ServiceControllerImpl<?>>();
            final LinkedHashMap<ServiceName, ServiceControllerImpl<?>> pathTrace = new LinkedHashMap<ServiceName, ServiceControllerImpl<?>>();
            for (ServiceControllerImpl<?> service: services) {
                if (checkedServices.contains(service) || service.getState(transaction) != ServiceControllerImpl.STATE_DOWN) {
                    continue;
                }
                final ServiceName serviceName = service.getPrimaryRegistration().getServiceName();
                pathTrace.put(serviceName, service);
                verifyCycle(service, pathTrace, checkedServices, context);
                checkedServices.add(service);
                pathTrace.remove(serviceName);
            }
        } finally {
            context.complete();
        }
    }

    private void verifyCycle(ServiceControllerImpl<?> service, LinkedHashMap<ServiceName, ServiceControllerImpl<?>> pathTrace, Set<ServiceControllerImpl<?>> checkedServices, ValidateContext context) {
        for (DependencyImpl<?> dependency: service.getDependencies()) {
            final ServiceControllerImpl<?> dependencyController = dependency.getDependencyRegistration().getController();
            if (dependencyController != null && !checkedServices.contains(dependencyController)) {
                if (pathTrace.containsValue(dependencyController)) {
                    final ServiceName[] cycle = pathTrace.keySet().toArray(new ServiceName[pathTrace.size()]);
                    context.addProblem(MSCLogger.SERVICE.dependencyCycle(cycle));
                    return;
                }
                final ServiceName dependencyName = dependency.getDependencyRegistration().getServiceName();
                pathTrace.put(dependencyName, dependencyController);
                verifyCycle(dependencyController, pathTrace, checkedServices, context);
                checkedServices.add(dependencyController);
                pathTrace.remove(dependencyName);
            }
        }
    }
}
