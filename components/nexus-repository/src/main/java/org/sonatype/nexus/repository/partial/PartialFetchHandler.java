package org.sonatype.nexus.repository.partial;

import java.util.List;

import javax.annotation.Nonnull;

import org.sonatype.nexus.repository.http.HttpResponses;
import org.sonatype.nexus.repository.view.Context;
import org.sonatype.nexus.repository.view.Handler;
import org.sonatype.nexus.repository.view.Headers;
import org.sonatype.nexus.repository.view.Payload;
import org.sonatype.nexus.repository.view.PayloadResponse;
import org.sonatype.nexus.repository.view.Request;
import org.sonatype.nexus.repository.view.Response;
import org.sonatype.nexus.repository.view.Status;

import com.google.common.collect.Range;
import org.apache.http.HttpStatus;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implements partial-fetch semantics (as per RFC 2616) for {@link Status#isSuccessful() successful} {@link
 * PayloadResponse}s.
 *
 * @since 3.0
 */
public class PartialFetchHandler
    implements Handler
{
  private final RangeParser rangeParser;

  public PartialFetchHandler(final RangeParser rangeParser) {
    this.rangeParser = checkNotNull(rangeParser);
  }

  @Nonnull
  @Override
  public Response handle(@Nonnull final Context context) throws Exception {
    final Response response = context.proceed();

    if (!response.getStatus().isSuccessful()) {
      // We don't interfere with failure responses
      return response;
    }

    if (!(response instanceof PayloadResponse)) {
      return response;
    }

    final PayloadResponse payloadResponse = (PayloadResponse) response;
    final Payload payload = payloadResponse.getPayload();

    if (payload.getSize() == Payload.UNKNOWN_SIZE) {
      // We can't do much if we don't know how big the payload is
      return response;
    }

    final String rangeHeader = getRangeHeader(context);
    if (rangeHeader == null) {
      return response;
    }

    final List<Range<Long>> ranges = rangeParser.determineRanges(rangeHeader);

    if (ranges.isEmpty()) {
      return response;
    }

    if (ranges.size() > 1) {
      return HttpResponses.notImplemented("Multiple ranges not supported.");
    }

    Range requestedRange = ranges.get(0);

    if (!isSatisfiable(requestedRange, payload.getSize())) {
      return HttpResponses.rangeNotSatisfiable(payload.getSize());
    }

    // Mutate the response
    return partialResponse(payloadResponse, payload, requestedRange);
  }

  /**
   * Mutate the response into one that returns part of the payload.
   */
  private PayloadResponse partialResponse(final PayloadResponse response, final Payload payload,
                                          final Range requestedRange)
  {
    response.setStatus(Status.success(HttpStatus.SC_PARTIAL_CONTENT));
    final Range<Long> rangeToSend = requestedRange.intersection(Range.closed(0L, payload.getSize() - 1));

    Payload partialPayload = new PartialPayload(payload, rangeToSend);

    response.setPayload(partialPayload);

    final Headers responseHeaders = response.getHeaders();
    // ResponseSender takes care of Content-Length header, via payload.size
    responseHeaders.set("Content-Range",
        rangeToSend.lowerEndpoint() + "-" + rangeToSend.upperEndpoint() + "/" + payload.getSize());

    return response;
  }

  private String getRangeHeader(final Context context) {
    final Request request = context.getRequest();
    return request.getHeaders().get("Range");
  }

  private boolean isSatisfiable(final Range<Long> range, final long contentSize) {
    if (!range.hasLowerBound()) {
      return true;
    }
    // Per RFC 2616, a requested range is satisfiable as long as its lower bound is within the content size.
    // Requests for ranges that extend beyond the content size are okay.
    return range.lowerEndpoint() < contentSize - 1;
  }
}
