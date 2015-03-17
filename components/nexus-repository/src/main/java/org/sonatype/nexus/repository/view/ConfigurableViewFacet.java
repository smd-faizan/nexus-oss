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
package org.sonatype.nexus.repository.view;

import javax.inject.Named;

import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.util.NestedAttributesMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Configurable {@link ViewFacet} implementation.
 *
 * @since 3.0
 */
@Named
public class ConfigurableViewFacet
    extends FacetSupport
    implements ViewFacet
{
  public static final String CONFIG_KEY = "view";

  private Router router;

  private Boolean online;

  public void configure(final Router router) {
    checkNotNull(router);
    checkState(this.router == null, "Router already configured");
    this.router = router;
  }

  @Override
  protected void doConfigure() throws Exception {
    NestedAttributesMap attributes = getRepository().getConfiguration().attributes(CONFIG_KEY);
    online = attributes.get("online", Boolean.class, true);
    log.debug("Online: {}", online);
  }

  @Override
  public Response dispatch(final Request request) throws Exception {
    checkState(router != null, "Router not configured");
    return router.dispatch(getRepository(), request);
  }

  @Override
  public boolean isOnline() {
    return online;
  }
}
