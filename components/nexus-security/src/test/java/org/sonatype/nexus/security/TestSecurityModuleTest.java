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
package org.sonatype.nexus.security;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.sonatype.nexus.security.realm.RealmConfiguration;
import org.sonatype.sisu.ehcache.CacheManagerComponent;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import net.sf.ehcache.CacheManager;
import org.apache.shiro.cache.ehcache.EhCacheManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.nexus.TestSessionManager;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.SpaceModule;
import org.eclipse.sisu.space.URLClassSpace;
import org.eclipse.sisu.wire.ParameterKeys;
import org.eclipse.sisu.wire.WireModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;

/**
 * Verifies functionality of {@link TestSecurityModule}.
 */
public class TestSecurityModuleTest
    extends TestSupport
{
  private Injector injector;

  @Before
  public void setUp() {
    injector = Guice.createInjector(getWireModule());
  }

  @Test
  public void testInjectionIsSetupCorrectly() throws Exception {
    SecuritySystem securitySystem = injector.getInstance(SecuritySystem.class);
    // See DefaultSecuritySystem, that applies cache
    // TODO: this should be done with Guice binding?
    securitySystem.start();

    SecurityManager securityManager = injector.getInstance(SecurityManager.class);

    RealmSecurityManager realmSecurityManager = injector.getInstance(RealmSecurityManager.class);

    assertThat(securitySystem.getRealmSecurityManager(), sameInstance(securityManager));
    assertThat(securitySystem.getRealmSecurityManager(), sameInstance(realmSecurityManager));

    assertThat(securityManager, instanceOf(DefaultSecurityManager.class));
    DefaultSecurityManager defaultSecurityManager = (DefaultSecurityManager) securityManager;

    assertThat(defaultSecurityManager.getSessionManager(), instanceOf(TestSessionManager.class));
    TestSessionManager sessionManager = (TestSessionManager) defaultSecurityManager.getSessionManager();
    assertThat(sessionManager.getSessionDAO(), instanceOf(EnterpriseCacheSessionDAO.class));
    assertThat(((EhCacheManager) ((EnterpriseCacheSessionDAO) sessionManager.getSessionDAO()).getCacheManager()).getCacheManager(), sameInstance(injector.getInstance(CacheManagerComponent.class).getCacheManager()));
  }

  @After
  public void stopCache() {
    if (injector != null) {
      injector.getInstance(CacheManager.class).shutdown();
    }
  }

  private Module getWireModule() {
    return new WireModule(new TestSecurityModule(), getTestModule(), getSpaceModule(), getPropertiesModule());
  }

  private Module getTestModule() {
    return new Module()
    {
      @Override
      public void configure(final Binder binder) {
        RealmConfiguration realmConfiguration = new RealmConfiguration();
        realmConfiguration.setRealmNames(Arrays.asList(
            "MockRealmA",
            "MockRealmB"
        ));
        binder.bind(RealmConfiguration.class)
            .annotatedWith(Names.named("initial"))
            .toInstance(realmConfiguration);
      }
    };
  }

  private Module getSpaceModule() {
    return new SpaceModule(new URLClassSpace(getClass().getClassLoader()), BeanScanning.INDEX);
  }

  protected AbstractModule getPropertiesModule() {
    return new AbstractModule()
    {
      @Override
      protected void configure() {
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("application-conf", util.resolvePath("target/plexus-home/etc"));
        binder().bind(ParameterKeys.PROPERTIES).toInstance(properties);
      }
    };
  }
}
