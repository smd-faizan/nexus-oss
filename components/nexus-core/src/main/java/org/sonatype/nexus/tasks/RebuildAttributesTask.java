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

import java.util.List;

import javax.inject.Named;

import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RepositoryTaskSupport;

/**
 * Rebuild attributes task.
 */
@Named
public class RebuildAttributesTask
    extends RepositoryTaskSupport
{
  @Override
  public Void execute()
      throws Exception
  {
    ResourceStoreRequest req = new ResourceStoreRequest(getConfiguration().getPath());
    if (getConfiguration().getRepositoryId() != null) {
      getRepositoryRegistry().getRepository(getConfiguration().getRepositoryId()).recreateAttributes(req, null);
    }
    else {
      final List<Repository> reposes = getRepositoryRegistry().getRepositories();
      for (Repository repo : reposes) {
        repo.recreateAttributes(req, null);
      }
    }

    return null;
  }

  @Override
  public String getMessage() {
    if (getConfiguration().getRepositoryId() != null) {
      return "Rebuilding attributes of repository '" + getConfiguration().getRepositoryId() + "' from path "
          + getConfiguration().getPath() + " and below.";
    }
    else {
      return "Rebuilding attributes of all registered repositories from path " + getConfiguration().getPath()
          + " and below.";
    }
  }
}
