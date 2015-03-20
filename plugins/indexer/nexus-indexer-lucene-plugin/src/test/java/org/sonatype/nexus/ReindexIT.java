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
package org.sonatype.nexus;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;

import org.sonatype.nexus.index.AbstractIndexerManagerTest;
import org.sonatype.nexus.index.DefaultIndexerManager;
import org.sonatype.nexus.proxy.maven.MavenProxyRepository;
import org.sonatype.tests.http.runner.junit.ServerResource;
import org.sonatype.tests.http.server.fluent.Server;
import org.sonatype.tests.http.server.jetty.behaviour.filesystem.Get;
import org.sonatype.tests.http.server.jetty.behaviour.filesystem.Head;

import com.google.inject.Module;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.NexusIndexer;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.packer.IndexPacker;
import org.apache.maven.index.packer.IndexPackingRequest;
import org.eclipse.sisu.plexus.PlexusSpaceModule;
import org.eclipse.sisu.space.BeanScanning;
import org.eclipse.sisu.space.URLClassSpace;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

// This is an IT just because it runs longer then 15 seconds
public class ReindexIT
    extends AbstractIndexerManagerTest
{
  public static final long A_DAY_MILLIS = 24 * 60 * 60 * 1000;

  @Rule
  public ServerResource serverResource = new ServerResource(Server.server()
      .serve("/central/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central")), new Head(util.resolveFile("target/test-classes/reposes-remote/central")))
      .serve("/central-inc1/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc1")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc1")))
      .serve("/central-inc2/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc2")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc2")))
      .serve("/central-inc3/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc3")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc3")))
      .serve("/central-inc1-v1/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc1-v1")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc1-v1")))
      .serve("/central-inc2-v1/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc2-v1")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc2-v1")))
      .serve("/central-inc3-v1/*").withBehaviours(new Get(util.resolvePath("target/test-classes/reposes-remote/central-inc3-v1")), new Head(util.resolveFile("target/test-classes/reposes-remote/central-inc3-v1")))
      .getServerProvider());

  private NexusIndexer nexusIndexer;

  private IndexPacker indexPacker;

  @Override
  protected void setUp()
      throws Exception
  {
    super.setUp();

    nexusIndexer = lookup(NexusIndexer.class);

    indexPacker = lookup(IndexPacker.class);
  }

  protected void makeCentralPointTo(String repoId)
      throws Exception
  {
    MavenProxyRepository central =
        repositoryRegistry.getRepositoryWithFacet("central", MavenProxyRepository.class);

    // redirect it to our "sppof" jetty (see ReindexTest.xml in src/test/resources....
    central.setRemoteUrl(serverResource.getServerProvider().getUrl() + "/" + repoId + "/");

    // make the central download the remote indexes is found
    central.setDownloadRemoteIndexes(true);

    nexusConfiguration().saveConfiguration();

    waitForTasksToStop();
  }

  protected File getIndexFamilyDirectory(String path) {
    File indexDirectory = new File(new File(getBasedir()), "target/indexFamily/" + path);

    return indexDirectory;
  }

  protected File getRemoteRepositoryRoot(String path) {
    // Be aware, that "name" != "repoId"! For example, "central-inc1", "central-inc2"... are all "slices" of
    // "central" repo in different time!
    File root = new File(new File(getBasedir()), "target/test-classes/reposes-remote/" + path);

    return root;
  }

  protected void shiftContextInTime(IndexingContext ctx, int shiftDays)
      throws IOException
  {
    if (shiftDays != 0) {
      final IndexWriter iw = ctx.getIndexWriter();
      final IndexSearcher is = ctx.acquireIndexSearcher();
      try {
        final IndexReader ir = is.getIndexReader();
        for (int docNum = 0; docNum < ir.maxDoc(); docNum++) {
          if (!ir.isDeleted(docNum)) {
            Document doc = ir.document(docNum);

            String lastModified = doc.get(ArtifactInfo.LAST_MODIFIED);

            if (lastModified != null) {
              long lm = Long.parseLong(lastModified);

              lm = lm + (shiftDays * A_DAY_MILLIS);

              doc.removeFields(ArtifactInfo.LAST_MODIFIED);

              doc.add(new Field(ArtifactInfo.LAST_MODIFIED, Long.toString(lm), Field.Store.YES,
                  Field.Index.NO));

              iw.updateDocument(new Term(ArtifactInfo.UINFO, doc.get(ArtifactInfo.UINFO)), doc);
            }
          }
        }

        ctx.optimize();

        ctx.commit();

        // shift timestamp too
        if (ctx.getTimestamp() != null) {
          ctx.updateTimestamp(true, new Date(ctx.getTimestamp().getTime() + (shiftDays * A_DAY_MILLIS)));
        }
        else {
          ctx.updateTimestamp(true, new Date(System.currentTimeMillis() + (shiftDays * A_DAY_MILLIS)));
        }
      }
      finally {
        ctx.releaseIndexSearcher(is);
      }
    }
  }

  /**
   * Will reindex, shift if needed and publish indexes for a "remote" repository (published over jetty component).
   */
  protected void reindexRemoteRepositoryAndPublish(File repositoryRoot, String repositoryId,
                                                   boolean deleteIndexFiles, int shiftDays)
      throws IOException
  {
    File indexDirectory = getIndexFamilyDirectory(repositoryId);

    Directory directory = FSDirectory.open(indexDirectory);

    IndexingContext ctx =
        nexusIndexer.addIndexingContextForced(repositoryId + "-temp", repositoryId, repositoryRoot, directory,
            null, null, new IndexCreatorHelper(getTestInjector()).getFullCreators());

    // shifting if needed (very crude way to do it, but heh)
    shiftContextInTime(ctx, shiftDays);

    // and scan "today"
    nexusIndexer.scan(ctx);

    ctx.updateTimestamp(true);

    // pack it up
    File targetDir = new File(repositoryRoot, ".index");

    targetDir.mkdirs();

    IndexPackingRequest ipr = new IndexPackingRequest(ctx, targetDir);

    ipr.setCreateIncrementalChunks(true);

    indexPacker.packIndex(ipr);

    nexusIndexer.removeIndexingContext(ctx, deleteIndexFiles);
  }

  protected void validateIndexWithIdentify(boolean shouldBePresent, String sha1Hash, String gid, String aid,
                                           String version)
      throws Exception
  {
    Collection<ArtifactInfo> ais = indexerManager.identifyArtifact(MAVEN.SHA1, sha1Hash);

    if (shouldBePresent) {
      assertTrue("Should find " + gid + ":" + aid + ":" + version, ais.size() > 0);

      ArtifactInfo ai = ais.iterator().next();

      assertEquals(gid, ai.groupId);
      assertEquals(aid, ai.artifactId);
      assertEquals(version, ai.version);
    }
    else {
      assertEquals("Should not find " + gid + ":" + aid + ":" + version, 0, ais.size());
    }
  }

  @Test
  public void testHostedRepositoryReindex()
      throws Exception
  {
    fillInRepo();

    indexerManager.reindexRepository(null, "releases", true);

    validateIndexWithIdentify(true, "86e12071021fa0be4ec809d4d2e08f07b80d4877", "org.sonatype.nexus",
        "nexus-indexer", "1.0-beta-4");
  }

  @Test
  public void testProxyRepositoryReindex()
      throws Exception
  {
    fillInRepo();

    reindexRemoteRepositoryAndPublish(getRemoteRepositoryRoot("central"), "central", true, 0);

    makeCentralPointTo("central");

    indexerManager.reindexRepository(null, "central", true);

    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
  }

  @Test
  public void testGroupReindex()
      throws Exception
  {
    fillInRepo();

    reindexRemoteRepositoryAndPublish(getRemoteRepositoryRoot("central"), "central", true, 0);

    makeCentralPointTo("central");

    // central is member of public group
    indexerManager.reindexRepository(null, "public", true);

    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
  }

  @Test
  public void testCurrentIncrementalIndexes()
      throws Exception
  {
    // day 1
    reindexRemoteRepositoryAndPublish(getRemoteRepositoryRoot("central-inc1"), "central", false, 0);

    makeCentralPointTo("central-inc1");

    indexerManager.reindexRepository(null, "central", true);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(false, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(false, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(false, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");

    // day 2 (1 day passed), so shift both ctxes "in time"
    reindexRemoteRepositoryAndPublish(getRemoteRepositoryRoot("central-inc2"), "central", false, -1);
    shiftContextInTime(((DefaultIndexerManager) indexerManager).getRepositoryIndexContext("central"), -1);

    makeCentralPointTo("central-inc2");

    indexerManager.reindexRepository(null, "central", false);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(true, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(false, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");

    // day 3
    reindexRemoteRepositoryAndPublish(getRemoteRepositoryRoot("central-inc3"), "central", false, -1);
    shiftContextInTime(((DefaultIndexerManager) indexerManager).getRepositoryIndexContext("central"), -1);

    makeCentralPointTo("central-inc3");

    indexerManager.reindexRepository(null, "central", false);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(true, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(true, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");
  }

  @Test
  public void testV1IncrementalIndexes()
      throws Exception
  {
    // day 1
    makeCentralPointTo("central-inc1-v1");

    indexerManager.reindexRepository(null, "central", true);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(false, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(false, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(false, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");

    // day 2
    makeCentralPointTo("central-inc2-v1");

    indexerManager.reindexRepository(null, "central", false);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(true, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(false, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");

    // day 3
    makeCentralPointTo("central-inc3-v1");

    indexerManager.reindexRepository(null, "central", false);

    // validation
    validateIndexWithIdentify(true, "cf4f67dae5df4f9932ae7810f4548ef3e14dd35e", "antlr", "antlr", "2.7.6");
    validateIndexWithIdentify(true, "83cd2cd674a217ade95a4bb83a8a14f351f48bd0", "antlr", "antlr", "2.7.7");

    validateIndexWithIdentify(true, "3640dd71069d7986c9a14d333519216f4ca5c094", "log4j", "log4j", "1.2.8");
    validateIndexWithIdentify(true, "057b8740427ee6d7b0b60792751356cad17dc0d9", "log4j", "log4j", "1.2.12");
    validateIndexWithIdentify(true, "f0a0d2e29ed910808c33135a3a5a51bba6358f7b", "log4j", "log4j", "1.2.15");
  }

  @Override
  protected Module spaceModule() {
    return new PlexusSpaceModule(new URLClassSpace(getClassLoader()), BeanScanning.INDEX);
  }
}
