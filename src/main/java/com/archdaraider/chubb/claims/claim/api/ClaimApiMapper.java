package com.archdaraider.chubb.claims.claim.api;

import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ClaimDetailsResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ClaimResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.ExposureResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.TimelineResponse;
import com.archdaraider.chubb.claims.claim.api.ClaimResponses.WorkQueueResponse;
import com.archdaraider.chubb.claims.claim.application.ClaimDetails;
import com.archdaraider.chubb.claims.claim.application.ExposureItem;
import com.archdaraider.chubb.claims.claim.application.WorkQueueItem;
import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import com.archdaraider.chubb.claims.shared.api.ApiInputException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ClaimApiMapper {
  public ClaimType claimType(String value) {
    return Arrays.stream(ClaimType.values())
        .filter(type -> type.value().equalsIgnoreCase(normalize(value)))
        .findFirst()
        .orElseThrow(
            () -> new ApiInputException("claim_type_invalid", "type must be motor or property"));
  }

  public ClaimAction action(String value) {
    return Arrays.stream(ClaimAction.values())
        .filter(action -> action.value().equalsIgnoreCase(normalize(value)))
        .findFirst()
        .orElseThrow(
            () -> new ApiInputException("action_invalid", "the claim action is not recognized"));
  }

  public ClaimStatus queryStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return Arrays.stream(ClaimStatus.values())
        .filter(status -> status.value().equalsIgnoreCase(normalize(value)))
        .findFirst()
        .orElseThrow(
            () -> new ApiInputException("query_invalid", "the status filter is not recognized"));
  }

  public String queryMarket(String value) {
    var normalized = normalize(value);
    if (normalized == null || normalized.isBlank()) {
      return null;
    }
    if (normalized.length() != 2 || !normalized.chars().allMatch(Character::isLetter)) {
      throw new ApiInputException("query_invalid", "market must contain two letters");
    }
    return normalized.toUpperCase(Locale.ROOT);
  }

  public ClaimResponse claim(ClaimSnapshot snapshot) {
    return new ClaimResponse(
        snapshot.id(),
        snapshot.claimantId(),
        snapshot.claimType().value(),
        snapshot.market(),
        snapshot.incidentAt(),
        snapshot.description(),
        snapshot.estimatedLoss(),
        snapshot.currency(),
        snapshot.status().value(),
        snapshot.assigneeId(),
        snapshot.decisionReason(),
        snapshot.submittedAt(),
        snapshot.updatedAt(),
        snapshot.version());
  }

  public ClaimDetailsResponse details(ClaimDetails details) {
    return new ClaimDetailsResponse(
        claim(details.snapshot()),
        details.timeline().stream()
            .map(
                item ->
                    new TimelineResponse(
                        item.eventType(),
                        item.resultingStatus().value(),
                        item.actorId(),
                        item.detail(),
                        item.occurredAt()))
            .toList());
  }

  public WorkQueueResponse queueItem(WorkQueueItem item) {
    return new WorkQueueResponse(
        item.claimId(),
        item.claimantId(),
        item.claimType().value(),
        item.market(),
        item.status().value(),
        item.assigneeId(),
        item.estimatedLoss(),
        item.currency(),
        item.submittedAt(),
        item.updatedAt());
  }

  public ExposureResponse exposure(ExposureItem item) {
    return new ExposureResponse(item.currency(), item.amount(), item.claimCount());
  }

  private static String normalize(String value) {
    return value == null ? null : value.trim();
  }
}
