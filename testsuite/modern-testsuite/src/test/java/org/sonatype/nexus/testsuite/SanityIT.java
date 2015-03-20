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
package org.sonatype.nexus.testsuite;

import javax.inject.Inject;

import org.sonatype.nexus.SystemState;
import org.sonatype.nexus.SystemStatus;
import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;

import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Sanity test of the OSS base template.
 * 
 * @since 3.0
 */
public class SanityIT
    extends NexusPaxExamSupport
{
  @Configuration
  public static Option[] config() {
    return options(nexusDistribution("org.sonatype.nexus.assemblies", "nexus-base-template"));
  }

  @Inject
  private SystemStatus status;

  @Test
  public void testNexusStarts() {
    assertThat(SystemState.STARTED, is(status.getState()));
  }
}
