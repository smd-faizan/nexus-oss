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
package org.sonatype.nexus.repository.storage;

import javax.annotation.Nullable;

import org.sonatype.nexus.common.collect.NestedAttributesMap;

import org.joda.time.DateTime;

/**
 * Wraps an {@code OrientVertex} to provide a simpler API for working with stored component and asset metadata.
 *
 * @since 3.0
 */
public interface MetadataNode
    extends VertexWrapper
{
  /**
   * Gets the last updated date or {@code null} if undefined (the node has never been saved).
   */
  @Nullable
  DateTime lastUpdated();

  /**
   * Gets the last updated date or throws a runtime exception if undefined.
   */
  DateTime requireLastUpdated();

  /**
   * Gets the format property, which is immutable.
   */
  String format();

  /**
   * Gets the "attributes" property of this node, a map of maps that is possibly empty, but never {@code null}.
   */
  NestedAttributesMap attributes();

  /**
   * Gets the format-specific attributes of this node ("attributes.formatName").
   */
  NestedAttributesMap formatAttributes();
}
