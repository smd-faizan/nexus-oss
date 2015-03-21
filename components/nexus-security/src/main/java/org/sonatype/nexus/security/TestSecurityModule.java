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

import java.lang.reflect.Constructor;

import javax.inject.Singleton;

import org.sonatype.nexus.security.authc.FirstSuccessfulModularRealmAuthenticator;
import org.sonatype.nexus.security.authz.ExceptionCatchingModularRealmAuthorizer;

import com.google.common.base.Throwables;
import com.google.inject.binder.AnnotatedBindingBuilder;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.Authenticator;
import org.apache.shiro.authz.Authorizer;
import org.apache.shiro.config.ConfigurationException;
import org.apache.shiro.guice.ShiroModule;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.nexus.TestSessionManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.session.mgt.SessionManager;
import org.apache.shiro.session.mgt.eis.EnterpriseCacheSessionDAO;
import org.apache.shiro.session.mgt.eis.SessionDAO;

/**
 * Shiro security configuration Guice module for tests.
 *
 * @since 2.6.1
 *
 * @see WebSecurityModule
 */
public class TestSecurityModule
    extends ShiroModule
{
  @Override
  protected void configureShiro() {
    bindRealm().to(EmptyRealm.class); // not used in practice, just here to keep Shiro module happy

    // configure our preferred security components
    bind(SessionDAO.class).to(EnterpriseCacheSessionDAO.class).asEagerSingleton();
    bind(Authenticator.class).to(FirstSuccessfulModularRealmAuthenticator.class).in(Singleton.class);
    bind(Authorizer.class).to(ExceptionCatchingModularRealmAuthorizer.class).in(Singleton.class);
  }

  @Override
  protected void bindSecurityManager(final AnnotatedBindingBuilder<? super SecurityManager> bind) {
    // prefer the default constructor; we'll set the realms programatically
    bind(DefaultSecurityManager.class).toConstructor(ctor(DefaultSecurityManager.class)).asEagerSingleton();

    // bind RealmSecurityManager and SecurityManager to _same_ component
    bind(RealmSecurityManager.class).to(DefaultSecurityManager.class);
    bind.to(DefaultSecurityManager.class);

    // bindings used by external modules
    expose(RealmSecurityManager.class);
  }

  @Override
  protected void bindSessionManager(final AnnotatedBindingBuilder<SessionManager> bind) {
    // workaround for NEXUS-5727, see NexusDefaultSessionManager javadoc for clues
    bind.to(TestSessionManager.class).asEagerSingleton();
    // this is a PrivateModule, so explicitly binding the NexusDefaultSessionManager class
    bind(TestSessionManager.class);
  }

  /**
   * Empty {@link Realm} - only used to satisfy Shiro's need for an initial realm binding.
   */
  @Singleton
  private static final class EmptyRealm
      implements Realm
  {
    public String getName() {
      return getClass().getName();
    }

    public boolean supports(final AuthenticationToken token) {
      return false;
    }

    public AuthenticationInfo getAuthenticationInfo(final AuthenticationToken token) {
      return null;
    }
  }

  /**
   * @return Public constructor with given parameterTypes; wraps checked exceptions
   */
  private static <T> Constructor<T> ctor(final Class<T> clazz, final Class<?>... parameterTypes) {
    try {
      return clazz.getConstructor(parameterTypes);
    }
    catch (Exception e) {
      Throwables.propagateIfPossible(e);
      throw new ConfigurationException(e);
    }
  }
}
