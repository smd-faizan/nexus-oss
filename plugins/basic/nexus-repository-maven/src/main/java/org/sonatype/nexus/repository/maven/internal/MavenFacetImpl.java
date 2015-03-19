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
package org.sonatype.nexus.repository.maven.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobRef;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.common.io.TempStreamSupplier;
import org.sonatype.nexus.mime.MimeSupport;
import org.sonatype.nexus.repository.FacetSupport;
import org.sonatype.nexus.repository.content.InvalidContentException;
import org.sonatype.nexus.repository.maven.internal.MavenPath.Coordinates;
import org.sonatype.nexus.repository.maven.internal.policy.ChecksumPolicy;
import org.sonatype.nexus.repository.maven.internal.policy.VersionPolicy;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.payloads.BlobPayload;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.hash.HashCode;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.joda.time.DateTime;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static org.sonatype.nexus.common.hash.HashAlgorithm.MD5;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * A {@link MavenFacet} that persists Maven artifacts and metadata to a {@link StorageFacet}.
 * <p/>
 * Structure for artifacts (CMA components and assets):
 * <ul>
 * <li>CMA components: keyed by groupId:artifactId:version</li>
 * <li>CMA assets: keyed by path</li>
 * </ul>
 * <p/>
 * Structure for metadata (CMA assets only):
 * <ul>
 * <li>CMA assets: keyed by path</li>
 * </ul>
 * In both cases, "external" hashes are stored as separate asset, as their path differs too.
 *
 * @since 3.0
 */
@Named
public class MavenFacetImpl
    extends FacetSupport
    implements MavenFacet
{
  // artifact shared properties of both, artifact component and artifact asset

  private static final String P_GROUP_ID = "groupId";

  private static final String P_ARTIFACT_ID = "artifactId";

  private static final String P_VERSION = "version";

  private static final String P_BASE_VERSION = "baseVersion";

  private static final String P_CLASSIFIER = "classifier";

  private static final String P_EXTENSION = "extension";

  // artifact component properties

  private static final String P_COMPONENT_KEY = "key";

  // shared properties for both artifact and metadata assets

  private static final String P_ASSET_KEY = "key";

  private static final String P_CONTENT_LAST_MODIFIED = "contentLastModified";

  private static final String P_LAST_VERIFIED = "lastVerified";

  private static final String CONFIG_KEY = "maven";

  private static final List<HashAlgorithm> HASH_ALGORITHMS = Lists.newArrayList(SHA1, MD5);

  // members

  private final MimeSupport mimeSupport;

  private boolean strictContentTypeValidation;

  private VersionPolicy versionPolicy;

  private ChecksumPolicy checksumPolicy;

  @Inject
  public MavenFacetImpl(final MimeSupport mimeSupport) {
    this.mimeSupport = checkNotNull(mimeSupport);
  }

  @Override
  protected void doConfigure() throws Exception {
    super.doConfigure();
    NestedAttributesMap attributes = getRepository().getConfiguration().attributes(CONFIG_KEY);
    this.strictContentTypeValidation =
        attributes.get("strictContentTypeValidation", Boolean.class, Boolean.FALSE).booleanValue();
    this.versionPolicy = VersionPolicy.valueOf(
        attributes.require("versionPolicy", String.class)
    );
    this.checksumPolicy = ChecksumPolicy.valueOf(
        attributes.require("checksumPolicy", String.class)
    );
  }

  @Nonnull
  @Override
  public VersionPolicy getVersionPolicy() {
    return versionPolicy;
  }

  @Nonnull
  @Override
  public ChecksumPolicy getChecksumPolicy() {
    return checksumPolicy;
  }

  @Nullable
  @Override
  public BlobPayload get(final MavenPath path) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex asset = findAsset(tx, tx.getBucket(), path);
      if (asset == null) {
        return null;
      }
      final BlobRef blobRef = getBlobRef(path, asset);
      final Blob blob = tx.getBlob(blobRef);
      checkState(blob != null, "asset of component with at path %s refers to missing blob %s", path.getPath(), blobRef);
      final String contentType = asset.getProperty(StorageFacet.P_CONTENT_TYPE);

      final NestedAttributesMap attributesMap = getFormatAttributes(tx, asset);
      final Date lastModifiedDate = attributesMap.require(P_CONTENT_LAST_MODIFIED, Date.class);
      final NestedAttributesMap checksumAttributes = getAttributes(tx, asset).child(StorageFacet.P_CHECKSUM);
      final Map<HashAlgorithm, HashCode> hashCodes = Maps.newHashMap();
      for (HashAlgorithm algorithm : HASH_ALGORITHMS) {
        final HashCode hashCode = HashCode.fromString(checksumAttributes.require(algorithm.name(), String.class));
        hashCodes.put(algorithm, hashCode);
      }
      return new BlobPayload(blob, contentType, new DateTime(lastModifiedDate), hashCodes);
    }
  }

  @Override
  public void put(final MavenPath path, final Payload content)
      throws IOException, InvalidContentException
  {
    try (StorageTx tx = getStorage().openTx()) {
      if (path.getCoordinates() != null) {
        putArtifact(path, content, tx);
      }
      else {
        putFile(path, content, tx);
      }
      tx.commit();
    }
  }

  private void putArtifact(final MavenPath path, final Payload content, final StorageTx tx)
      throws IOException, InvalidContentException
  {
    final Coordinates coordinates = path.getCoordinates();
    OrientVertex component = findComponent(tx, tx.getBucket(), path);
    if (component == null) {
      component = tx.createComponent(tx.getBucket());

      // Set normalized properties: format, group, and name
      component.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue());
      component.setProperty(StorageFacet.P_PATH, path.getPath());
      component.setProperty(StorageFacet.P_GROUP, coordinates.getGroupId());
      component.setProperty(StorageFacet.P_NAME, coordinates.getArtifactId());
      component.setProperty(StorageFacet.P_VERSION, coordinates.getVersion());

      // Set format specific attributes
      final NestedAttributesMap componentAttributes = getFormatAttributes(tx, component);
      componentAttributes.set(P_COMPONENT_KEY, getComponentKey(coordinates));
      componentAttributes.set(P_GROUP_ID, coordinates.getGroupId());
      componentAttributes.set(P_ARTIFACT_ID, coordinates.getArtifactId());
      componentAttributes.set(P_VERSION, coordinates.getVersion());
      if (coordinates.isSnapshot()) {
        componentAttributes.set(P_BASE_VERSION, coordinates.getBaseVersion());
      }
    }

    OrientVertex asset = selectComponentAsset(tx, component, path);
    if (asset == null) {
      asset = tx.createAsset(tx.getBucket());

      asset.addEdge(StorageFacet.E_PART_OF_COMPONENT, component);
      asset.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue());
      asset.setProperty(StorageFacet.P_NAME, path.getFileName());
      asset.setProperty(StorageFacet.P_PATH, path.getPath());

      final NestedAttributesMap assetAttributes = getFormatAttributes(tx, asset);
      assetAttributes.set(P_ASSET_KEY, getAssetKey(path));
      assetAttributes.set(P_GROUP_ID, coordinates.getGroupId());
      assetAttributes.set(P_ARTIFACT_ID, coordinates.getArtifactId());
      assetAttributes.set(P_VERSION, coordinates.getVersion());
      if (coordinates.isSnapshot()) {
        assetAttributes.set(P_BASE_VERSION, coordinates.getBaseVersion());
      }
      assetAttributes.set(P_CLASSIFIER, coordinates.getClassifier());
      assetAttributes.set(P_EXTENSION, coordinates.getExtension());

      // TODO: if subordinate asset (sha1/md5/asc), should we link it somehow to main asset?
    }

    putAssetPayload(path, tx, asset, content);
  }

  private void putFile(final MavenPath path, final Payload content, final StorageTx tx)
      throws IOException, InvalidContentException
  {
    OrientVertex asset = findAsset(tx, tx.getBucket(), path);
    if (asset == null) {
      asset = tx.createAsset(tx.getBucket());
      asset.setProperty(StorageFacet.P_FORMAT, getRepository().getFormat().getValue());
      asset.setProperty(StorageFacet.P_NAME, path.getFileName());
      asset.setProperty(StorageFacet.P_PATH, path.getPath());

      final NestedAttributesMap assetAttributes = getFormatAttributes(tx, asset);
      assetAttributes.set(P_ASSET_KEY, getAssetKey(path));

      // TODO: if subordinate asset (sha1/md5/asc), should we link it somehow to main asset?
    }

    putAssetPayload(path, tx, asset, content);
  }

  private void putAssetPayload(final MavenPath path,
                               final StorageTx tx,
                               final OrientVertex asset,
                               final Payload content) throws IOException
  {
    // TODO: Figure out created-by header
    final ImmutableMap<String, String> headers = ImmutableMap.of(
        BlobStore.BLOB_NAME_HEADER, path.getPath(),
        BlobStore.CREATED_BY_HEADER, "unknown"
    );

    try (InputStream inputStream = content.openInputStream()) {
      try (TempStreamSupplier supplier = new TempStreamSupplier(inputStream)) {
        final String contentType = determineContentType(path, supplier, content.getContentType());
        try (InputStream is = supplier.get()) {
          tx.setBlob(is, headers, asset, HASH_ALGORITHMS, contentType);
        }
      }
    }

    final NestedAttributesMap assetAttributes = getFormatAttributes(tx, asset);
    assetAttributes.set(P_CONTENT_LAST_MODIFIED, new Date());
  }

  @Override
  public boolean delete(final MavenPath path) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      if (path.getCoordinates() != null) {
        return deleteArtifact(path, tx);
      }
      else {
        return deleteFile(path, tx);
      }
    }
  }

  private boolean deleteArtifact(final MavenPath path, final StorageTx tx) throws IOException {
    final OrientVertex component = findComponent(tx, tx.getBucket(), path);
    if (component == null) {
      return false;
    }
    final OrientVertex asset = selectComponentAsset(tx, component, path);
    if (asset == null) {
      return false;
    }
    final boolean lastAsset = tx.findAssets(component).size() == 1;
    tx.deleteBlob(getBlobRef(path, asset));
    tx.deleteVertex(asset);
    if (lastAsset) {
      tx.deleteVertex(component);
    }
    tx.commit();
    return true;
  }

  private boolean deleteFile(final MavenPath path, final StorageTx tx) throws IOException {
    final OrientVertex asset = findAsset(tx, tx.getBucket(), path);
    if (asset == null) {
      return false;
    }
    tx.deleteBlob(getBlobRef(path, asset));
    tx.deleteVertex(asset);
    tx.commit();
    return true;
  }

  @Override
  public DateTime getLastVerified(final MavenPath path) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex asset = findAsset(tx, tx.getBucket(), path);
      if (asset == null) {
        return null;
      }
      final NestedAttributesMap attributes = getFormatAttributes(tx, asset);
      final Date date = attributes.get(P_LAST_VERIFIED, Date.class);
      if (date == null) {
        return null;
      }
      return new DateTime(date);
    }
  }

  @Override
  public boolean setLastVerified(final MavenPath path, final DateTime verified) throws IOException {
    try (StorageTx tx = getStorage().openTx()) {
      final OrientVertex asset = findAsset(tx, tx.getBucket(), path);
      if (asset == null) {
        return false;
      }
      final NestedAttributesMap attributes = getFormatAttributes(tx, asset);
      attributes.set(P_LAST_VERIFIED, verified.toDate());
      tx.commit();
      return true;
    }
  }

  /**
   * Returns component key based on passed in {@link Coordinates} G:A:V values.
   */
  private String getComponentKey(final Coordinates coordinates) {
    // TODO: maybe sha1() the resulting string?
    return coordinates.getGroupId()
        + ":" + coordinates.getArtifactId()
        + ":" + coordinates.getVersion();
  }

  /**
   * Returns asset key based on passed in {@link MavenPath} path value.
   */
  private String getAssetKey(final MavenPath mavenPath) {
    // TODO: maybe sha1() the resulting string?
    return mavenPath.getPath();
  }

  /**
   * Finds component by key.
   */
  @Nullable
  private OrientVertex findComponent(final StorageTx tx,
                                     final OrientVertex bucket,
                                     final MavenPath mavenPath)
  {
    final String componentKeyName =
        StorageFacet.P_ATTRIBUTES + "." + getRepository().getFormat().getValue() + "." + P_COMPONENT_KEY;
    return tx.findComponentWithProperty(componentKeyName, getComponentKey(mavenPath.getCoordinates()), bucket);
  }

  /**
   * Selects a component asset by key.
   */
  @Nullable
  private OrientVertex selectComponentAsset(final StorageTx tx,
                                            final OrientVertex component,
                                            final MavenPath mavenPath)
  {
    final String assetKey = getAssetKey(mavenPath);
    final List<OrientVertex> assets = tx.findAssets(component);
    for (OrientVertex v : assets) {
      final NestedAttributesMap attributesMap = getFormatAttributes(tx, v);
      if (assetKey.equals(attributesMap.get(P_ASSET_KEY, String.class))) {
        return v;
      }
    }
    return null;
  }

  /**
   * Finds asset by key.
   */
  @Nullable
  private OrientVertex findAsset(final StorageTx tx,
                                 final OrientVertex bucket,
                                 final MavenPath mavenPath)
  {
    final String assetKeyName =
        StorageFacet.P_ATTRIBUTES + "." + getRepository().getFormat().getValue() + "." + P_ASSET_KEY;
    return tx.findAssetWithProperty(assetKeyName, getAssetKey(mavenPath), bucket);
  }

  private NestedAttributesMap getAttributes(final StorageTx tx, final OrientVertex vertex) {
    return tx.getAttributes(vertex);
  }

  private NestedAttributesMap getFormatAttributes(final StorageTx tx, final OrientVertex vertex) {
    return getAttributes(tx, vertex).child(getRepository().getFormat().getValue());
  }

  /**
   * Determines or confirms the content type for the content, or throws {@link InvalidContentException} if it cannot.
   */
  @Nonnull
  private String determineContentType(final MavenPath mavenPath,
                                      final Supplier<InputStream> inputStreamSupplier,
                                      final String declaredContentType)
      throws IOException
  {
    String contentType = declaredContentType;

    if (contentType == null) {
      log.trace("Content PUT to {} has no content type.", mavenPath);
      try (InputStream is = inputStreamSupplier.get()) {
        contentType = mimeSupport.detectMimeType(is, mavenPath.getPath());
      }
      log.trace("Mime support implies content type {}", contentType);

      if (contentType == null && strictContentTypeValidation) {
        throw new InvalidContentException(String.format("Content type could not be determined."));
      }
    }
    else {
      try (InputStream is = inputStreamSupplier.get()) {
        final List<String> types = mimeSupport.detectMimeTypes(is, mavenPath.getPath());
        if (!types.isEmpty() && !types.contains(contentType)) {
          log.debug("Discovered content type {} ", types.get(0));
          if (strictContentTypeValidation) {
            throw new InvalidContentException(
                String.format("Declared content type %s, but declared %s.", contentType, types.get(0)));
          }
        }
      }
    }
    return contentType;
  }

  private StorageFacet getStorage() {
    return getRepository().facet(StorageFacet.class);
  }

  private BlobRef getBlobRef(final MavenPath mavenPath, final OrientVertex asset) {
    String blobRefStr = asset.getProperty(StorageFacet.P_BLOB_REF);
    checkState(blobRefStr != null, "asset of component at path %s has missing blob reference",
        mavenPath.getPath());
    return BlobRef.parse(blobRefStr);
  }
}
