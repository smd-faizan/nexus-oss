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
package org.sonatype.nexus.plugins.mavenbridge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.sonatype.nexus.AbstractApplicationStatusSource;
import org.sonatype.nexus.ApplicationStatusSource;
import org.sonatype.nexus.NxApplication;
import org.sonatype.nexus.SystemStatus;
import org.sonatype.nexus.proxy.maven.AbstractMavenRepository;
import org.sonatype.nexus.proxy.maven.MavenGroupRepository;
import org.sonatype.nexus.proxy.maven.MavenHostedRepository;
import org.sonatype.nexus.proxy.maven.MavenProxyRepository;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.maven.gav.Gav;
import org.sonatype.nexus.proxy.registry.RepositoryRegistry;
import org.sonatype.nexus.security.subject.FakeAlmightySubject;
import org.sonatype.nexus.test.NexusTestSupport;
import org.sonatype.tests.http.server.fluent.Behaviours;
import org.sonatype.tests.http.server.fluent.Server;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import org.apache.shiro.mgt.RealmSecurityManager;
import org.apache.shiro.util.ThreadContext;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.sisu.plexus.PlexusSpaceModule;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.URLClassSpace;
import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvingIT
    extends NexusTestSupport
{
  protected NexusMavenBridge mavenBridge;

  protected RepositoryRegistry repositoryRegistry;

  private Server server;

  @Override
  protected void customizeModules(final List<Module> modules) {
    super.customizeModules(modules);
    modules.add(new AbstractModule()
    {
      @Override
      protected void configure() {
        ThreadContext.bind(FakeAlmightySubject.forUserId("disabled-security"));
        bind(RealmSecurityManager.class).toInstance(mock(RealmSecurityManager.class));

        ApplicationStatusSource statusSource = mock(AbstractApplicationStatusSource.class);
        when(statusSource.getSystemStatus()).thenReturn(new SystemStatus());
        bind(ApplicationStatusSource.class).toInstance(statusSource);
      }
    });
  }

  @Override
  protected void setUp()
      throws Exception
  {
    super.setUp();

    mavenBridge = lookup(NexusMavenBridge.class);

    repositoryRegistry = lookup(RepositoryRegistry.class);

    lookup(NxApplication.class).start();

    server = Server.withPort(0).serve("/*").withBehaviours(Behaviours.get(
        new File(getBasedir(), "src/test/resources/test-repo"))).start();

    for (MavenProxyRepository repo : repositoryRegistry.getRepositoriesWithFacet(MavenProxyRepository.class)) {
      repo.setRemoteUrl(server.getUrl().toExternalForm());
      ((AbstractMavenRepository) repo).commitChanges();
    }
  }

  @Override
  protected void tearDown()
      throws Exception
  {
    lookup(NxApplication.class).stop();

    super.tearDown();

    if (server != null) {
      server.stop();
    }
  }

  @Test
  public void testAetherResolveAgainstPublicGroup()
      throws Exception
  {
    ArrayList<MavenRepository> participants = new ArrayList<MavenRepository>();

    participants.add(repositoryRegistry.getRepositoryWithFacet("public", MavenGroupRepository.class));

    Gav gav = new Gav("org.apache.maven", "apache-maven", "3.0-beta-1");

    Assert.assertEquals("Root with 27 nodes was expected!", 27, resolve(participants, gav));
  }

  @Test
  public void testAetherResolveAgainstCentralRepository()
      throws Exception
  {
    ArrayList<MavenRepository> participants = new ArrayList<MavenRepository>();

    participants.add(repositoryRegistry.getRepositoryWithFacet("central", MavenProxyRepository.class));

    Gav gav = new Gav("org.apache.maven", "apache-maven", "3.0-beta-1");

    Assert.assertEquals("Root with 27 nodes was expected!", 27, resolve(participants, gav));
  }

  @Test
  public void testAetherResolveAgainstReleasesRepositoryThatShouldFail()
      throws Exception
  {
    ArrayList<MavenRepository> participants = new ArrayList<MavenRepository>();

    participants.add(repositoryRegistry.getRepositoryWithFacet("releases", MavenHostedRepository.class));

    Gav gav = new Gav("org.apache.maven", "apache-maven", "3.0-beta-1");

    Assert.assertEquals("Only the root node was expected!", 1, resolve(participants, gav));
  }

  protected int resolve(List<MavenRepository> participants, Gav gav)
      throws DependencyCollectionException, ArtifactResolutionException
  {
    DependencyNode root =
        mavenBridge.collectDependencies(Utils.createDependencyFromGav(gav, "compile"), participants);

    return dump(root);
  }

  // ==

  protected static int dump(DependencyNode node) {
    return dump(node, "", 0);
  }

  protected static int dump(DependencyNode node, String indent, int count) {
    System.out.println(indent + node.getDependency());
    indent += "  ";
    int result = count + 1;
    for (DependencyNode child : node.getChildren()) {
      result += dump(child, indent, count);
    }
    return result;
  }

  @Override
  protected Module spaceModule() {
    return new PlexusSpaceModule(new URLClassSpace(getClassLoader()), BeanScanning.INDEX);
  }
}
