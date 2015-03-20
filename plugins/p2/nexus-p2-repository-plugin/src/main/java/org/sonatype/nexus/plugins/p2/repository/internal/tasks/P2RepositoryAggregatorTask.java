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
package org.sonatype.nexus.plugins.p2.repository.internal.tasks;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.plugins.p2.repository.P2RepositoryAggregator;
import org.sonatype.nexus.proxy.repository.RepositoryTaskSupport;

import static com.google.common.base.Preconditions.checkNotNull;

@Named
public class P2RepositoryAggregatorTask
    extends RepositoryTaskSupport
{
  private final P2RepositoryAggregator p2RepositoryAggregator;

  @Inject
  public P2RepositoryAggregatorTask(final P2RepositoryAggregator p2RepositoryAggregator)
  {
    this.p2RepositoryAggregator = checkNotNull(p2RepositoryAggregator);
  }

  @Override
  public String getMessage() {
    if (getConfiguration().getRepositoryId() != null) {
      return String.format("Rebuild p2 repository on repository [%s] from root path and bellow",
          getConfiguration().getRepositoryId());
    }
    else {
      return "Rebuild p2 repository for all repositories (with a P2 Repository Generator Capability enabled)";
    }
  }

  @Override
  protected Void execute()
      throws Exception
  {
    final String repositoryId = getConfiguration().getRepositoryId();
    if (repositoryId != null) {
      p2RepositoryAggregator.scanAndRebuild(repositoryId);
    }
    else {
      p2RepositoryAggregator.scanAndRebuild();
    }
    return null;
  }
}
