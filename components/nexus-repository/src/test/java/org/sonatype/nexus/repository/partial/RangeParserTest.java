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