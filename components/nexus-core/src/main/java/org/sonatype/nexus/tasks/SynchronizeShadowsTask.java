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
package org.sonatype.nexus.tasks;

import javax.inject.Named;

import org.sonatype.nexus.proxy.repository.ShadowRepository;
import org.sonatype.nexus.proxy.repository.RepositoryTaskSupport;

/**
 * Synchronize shadow task.
 */
@Named
public class SynchronizeShadowsTask
    extends RepositoryTaskSupport
{
  @Override
  protected Void execute()
      throws Exception
  {
    ShadowRepository shadow =
        getRepositoryRegistry().getRepositoryWithFacet(getConfiguration().getRepositoryId(), ShadowRepository.class);
    shadow.synchronizeWithMaster();
    return null;
  }

  @Override
  public String getMessage() {
    return "Synchronizing virtual repository '" + getConfiguration().getRepositoryId() +
        "' with it's master repository.";
  }
}
