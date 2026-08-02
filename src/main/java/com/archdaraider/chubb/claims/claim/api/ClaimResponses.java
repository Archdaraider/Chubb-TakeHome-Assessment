package com.archdaraider.chubb.claims.claim.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClaimResponses {
  private ClaimResponses() {}

  public record ClaimResponse(
      UUID id,
      String claimantId,
      String type,
      String market,
      Instant incidentAt,
      String description,
      BigDecimal estimatedLoss,
      String currency,
      String status,
      String assigneeId,
      String decisionReason,
      Instant submittedAt,
      Instant updatedAt,
      Long version) {}

  public record TimelineResponse(
      String eventType,
      String resultingStatus,
      String actorId,
      String detail,
      Instant occurredAt) {}

  public record ClaimDetailsResponse(ClaimResponse claim, List<TimelineResponse> timeline) {
    public ClaimDetailsResponse {
      timeline = List.copyOf(timeline);
    }
  }

  public record WorkQueueResponse(
      UUID claimId,
      String claimantId,
      String type,
      String market,
      String status,
      String assigneeId,
      BigDecimal estimatedLoss,
      String currency,
      Instant submittedAt,
      Instant updatedAt) {}

  public record ExposureResponse(String currency, BigDecimal amount, long claimCount) {}
}
