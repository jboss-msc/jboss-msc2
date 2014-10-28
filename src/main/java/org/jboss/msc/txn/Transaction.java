/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import java.util.concurrent.TimeUnit;

import org.jboss.msc.problem.ProblemReport;
import org.jboss.msc.util.Attachable;

/**
 * There are two subtypes of transactions:
 *
 * <UL>
 *     <LI><B>UpdateTransaction</B> - modifying transactions that modify internal MSC states</LI>
 *     <LI><B>ReadTransaction</B> - read-only transactions that don't modify internal MSC states</LI>
 * </UL>
 *
 * Every <B>Transaction</B> is associated with <B>TransactionController</B> that created it.
 * At every point of time either single <B>UpdateTransaction</B> is running
 * or multiple <B>ReadTransaction</B>s are executing concurrently. This restriction
 * applies only to transactions created by the same <B>TransactionController</B>.
 * In other words it is possible to have multiple <B>UpdateTransaction</B>s running at the same time,
 * but in that case they have been created by distinct <B>TransactionController</B>s.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @see org.jboss.msc.txn.UpdateTransaction
 * @see org.jboss.msc.txn.ReadTransaction
 * @see org.jboss.msc.txn.TransactionController
 */
public interface Transaction extends Attachable {

    /**
     * Indicates whether this transaction have been terminated.
     * @return {@code true} if already terminated, {@code false} otherwise
     */
    boolean isTerminated();

    /**
     * Returns how long is/was transaction running.
     * @param unit time unit
     * @return how long is/was transaction running
     */
    long getDuration(TimeUnit unit);

    /**
     * Returns transaction problem report.
     * @return transaction problem report
     */
    ProblemReport getReport();

}
