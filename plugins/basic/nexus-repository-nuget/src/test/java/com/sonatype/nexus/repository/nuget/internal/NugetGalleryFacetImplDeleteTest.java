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
package com.sonatype.nexus.repository.nuget.internal;

import org.sonatype.nexus.repository.Repository;

import org.sonatype.nexus.blobstore.api.BlobRef;
import org.sonatype.nexus.repository.storage.ComponentDeletedEvent;
import org.sonatype.nexus.repository.storage.ComponentEvent;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.sisu.goodies.eventbus.EventBus;
import org.sonatype.sisu.litmus.testsupport.TestSupport;

import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies component deletion from a nuget gallery facet.
 */
public class NugetGalleryFacetImplDeleteTest
    extends TestSupport
{
  
  @Test
  public void deleteRemovesComponentAssetAndBlob() throws Exception {
    final String packageId = "screwdriver";
    final String version = "0.1.1";

    final EventBus eventBus = mock(EventBus.class);
    final Repository repository = mock(Repository.class);

    final NugetGalleryFacetImpl galleryFacet = Mockito.spy(new NugetGalleryFacetImpl()
    {
      @Override
      protected EventBus getEventBus() {
        return eventBus;
      }

      @Override
      protected Repository getRepository() {
        return repository;
      }
    });
    final StorageTx tx = mock(StorageTx.class);
    doReturn(tx).when(galleryFacet).openStorageTx();

    final OrientVertex component = mock(OrientVertex.class);
    final OrientVertex asset = mock(OrientVertex.class);
    final BlobRef blobRef = new BlobRef("local", "default", "a34af31");

    // Wire the mock vertices together: component has asset, asset has blobRef
    doReturn(component).when(galleryFacet).findComponent(tx, packageId, version);
    when(tx.findAssets(eq(component))).thenReturn(asList(asset));
    when(asset.getProperty(eq(StorageFacet.P_BLOB_REF))).thenReturn(blobRef.toString());

    galleryFacet.delete(packageId, version);

    // Verify that everything got deleted
    verify(tx).deleteVertex(component);
    verify(tx).deleteVertex(asset);
    verify(tx).deleteBlob(eq(blobRef));
    ArgumentCaptor<ComponentEvent> o = ArgumentCaptor.forClass(ComponentEvent.class);
    verify(eventBus, times(1)).post(o.capture());
    ComponentEvent actual = o.getValue();
    assertThat(actual, instanceOf(ComponentDeletedEvent.class));
    assertThat(actual.getVertex(), is(component));
    assertThat(actual.getRepository(), is(repository));
  }
}
