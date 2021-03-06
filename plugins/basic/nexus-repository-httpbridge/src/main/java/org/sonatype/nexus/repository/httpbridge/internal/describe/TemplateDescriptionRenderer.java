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
package org.sonatype.nexus.repository.httpbridge.internal.describe;

import java.net.URL;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.sisu.goodies.template.TemplateEngine;
import org.sonatype.sisu.goodies.template.TemplateParameters;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Renders an HTML description of a {@link Description} with a Velocity template.
 *
 * @since 3.0
 */
@Named
@Singleton
public class TemplateDescriptionRenderer
    implements DescriptionRenderer
{
  private static final String TEMPLATE_RESOURCE = "describe.vm";

  private final TemplateEngine templateEngine;

  private final URL template;

  @Inject
  public TemplateDescriptionRenderer(final @Named("shared") TemplateEngine templateEngine) {
    this.templateEngine = checkNotNull(templateEngine);
    template = getClass().getResource(TEMPLATE_RESOURCE);
  }

  @Override
  public String renderHtml(final Description description) {
    final TemplateParameters params = new TemplateParameters();
    params.setAll(description.getParameters());
    params.set("items", description.getItems());

    return templateEngine.render(this, template, params);
  }
}
