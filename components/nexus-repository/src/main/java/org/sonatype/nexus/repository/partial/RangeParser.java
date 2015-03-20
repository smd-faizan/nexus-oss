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
package org.sonatype.nexus.repository.partial;

import java.util.Collections;
import java.util.List;

import org.sonatype.sisu.goodies.common.ComponentSupport;

import com.google.common.base.Strings;
import com.google.common.collect.Range;

/**
 * Parses the "Range" request header.
 *
 * Defined by <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.35">RFC 2616 14.35</a>
 *
 * @since 3.0
 */
class RangeParser
    extends ComponentSupport
{
  public List<Range<Long>> determineRanges(final String rangeHeader) {
    // TODO: Current limitation: only one Range of bytes supported in forms of "-X", "X-Y" (where X<Y) and "X-".
    if (!Strings.isNullOrEmpty(rangeHeader)) {
      try {
        if (rangeHeader.startsWith("bytes=") && rangeHeader.length() > 6 && !rangeHeader.contains(",")) {
          // Range: bytes=500-999 (from 500th byte to 999th)
          // Range: bytes=500- (from 500th byte to the end)
          // Range: bytes=-999 (from 0th byte to the 999th byte, not by RFC but widely supported)
          final String rangeValue = rangeHeader.substring(6, rangeHeader.length());
          if (rangeValue.startsWith("-")) {
            return Collections.singletonList(Range.closed(0L, Long.parseLong(rangeValue.substring(1))));
          }
          else if (rangeValue.endsWith("-")) {
            return Collections
                .singletonList(Range.atLeast(Long.parseLong(rangeValue.substring(0, rangeValue.length() - 1))));
          }
          else if (rangeValue.contains("-")) {
            final String[] parts = rangeValue.split("-");
            return Collections.singletonList(Range.closed(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
          }
          else {
            log.info("Malformed HTTP Range value: {}, ignoring it", rangeHeader);
          }
        }
        else {
          log.info(
              "Nexus does not support non-byte or multiple HTTP Ranges, sending complete content: Range value {}",
              rangeHeader);
        }
      }
      catch (Exception e) {
        if (log.isDebugEnabled()) {
          log.info("Problem parsing Range value: {}, ignoring it", rangeHeader, e);
        }
        else {
          log.info("Problem parsing Range value: {}, ignoring it", rangeHeader);
        }
      }
    }
    return Collections.emptyList();
  }
}
