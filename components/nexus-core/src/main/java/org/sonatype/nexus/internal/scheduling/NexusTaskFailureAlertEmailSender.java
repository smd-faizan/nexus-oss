/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-2015 Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.internal.scheduling;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.email.NexusPostOffice;
import org.sonatype.nexus.events.Asynchronous;
import org.sonatype.nexus.events.EventSubscriber;
import org.sonatype.nexus.scheduling.Task;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.scheduling.events.TaskEventStoppedFailed;
import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * {@link EventSubscriber} that will send alert email (if necessary) in case of a failing {@link Task}.
 */
@Singleton
@Named
public class NexusTaskFailureAlertEmailSender
    extends ComponentSupport
    implements EventSubscriber, Asynchronous
{
  private final NexusPostOffice postOffice;

  @Inject
  public NexusTaskFailureAlertEmailSender(final NexusPostOffice postOffice) {
    this.postOffice = checkNotNull(postOffice);
  }

  /**
   * Sends alert emails if necessary. {@inheritDoc}
   */
  @Subscribe
  @AllowConcurrentEvents
  public void inspect(final TaskEventStoppedFailed failureEvent) {
    final TaskInfo failedTask = failureEvent.getTaskInfo();
    if (failedTask == null || failedTask.getConfiguration().getAlertEmail() == null) {
      return;
    }
    postOffice.sendNexusTaskFailure(
        failedTask.getConfiguration().getAlertEmail(),
        failedTask.getId(),
        failedTask.getName(),
        failureEvent.getFailureCause()
    );
  }
}
