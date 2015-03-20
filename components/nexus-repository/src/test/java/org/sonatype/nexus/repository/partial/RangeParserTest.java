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

import java.util.List;

import com.google.common.collect.Range;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class RangeParserTest
{
  @Test
  public void first500Bytes() {
    final List<Range<Long>> ranges = new RangeParser().determineRanges("bytes=0-499");
    assertThat(ranges.size(), is(1));
    assertThat(ranges.get(0), is(Range.closed(0L, 499L)));
  }

  @Test
  public void second500Bytes() {
    final List<Range<Long>> ranges = new RangeParser().determineRanges("bytes=500-999");
    assertThat(ranges.size(), is(1));
    assertThat(ranges.get(0), is(Range.closed(500L, 999L)));
  }

  public void last500Bytes() {
    final List<Range<Long>> ranges = new RangeParser().determineRanges("bytes=9500-");
    assertThat(ranges.size(), is(1));
    assertThat(ranges.get(0), is(Range.atLeast(9500L)));
  }

  @Test
  public void last500BytesSuffix() {
    final List<Range<Long>> ranges = new RangeParser().determineRanges("bytes=-500");
    assertThat(ranges.size(), is(1));
    assertThat(ranges.get(0), is(Range.atLeast(9500L)));
  }
}