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
package org.sonatype.nexus.testsuite.ssl;

//import javax.inject.Inject;
//
//import org.sonatype.nexus.bundle.launcher.NexusBundleConfiguration;
//import org.sonatype.sisu.bl.support.port.PortReservationService;

import org.junit.Ignore;

/**
 * ITs related to SMTP keys / access.
 */
@Ignore("FIXME: Updates for REST client required")
public class SSLSmtpIT
//    extends SSLITSupport
{

//  @Inject
//  private PortReservationService portReservationService;
//
//  @Override
//  protected NexusBundleConfiguration configureNexus(final NexusBundleConfiguration configuration) {
//    return super.configureNexus(configuration)
//        .setSystemProperty(
//            "org.sonatype.nexus.ssl.smtp.checkServerIdentity", "false"
//        );
//  }

  ///**
  // * Verify SMTP trust store key.
  // */
  //@Test
  //public void manageSMTPTrustStoreKey()
  //    throws Exception
  //{
  //  assertThat(truststore().isEnabledFor(smtpTrustStoreKey()), is(false));
  //
  //  truststore().enableFor(smtpTrustStoreKey());
  //  assertThat(truststore().isEnabledFor(smtpTrustStoreKey()), is(true));
  //
  //  truststore().disableFor(smtpTrustStoreKey());
  //  assertThat(truststore().isEnabledFor(smtpTrustStoreKey()), is(false));
  //}

}
