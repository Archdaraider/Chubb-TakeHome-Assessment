package com.archdaraider.chubb.claims.claim.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class Claim {
  private final UUID id;
  private final String claimantId;
  private final ClaimType claimType;
  private final String market;
  private final Instant incidentAt;
  private final String description;
  private final BigDecimal estimatedLoss;
  private final String currency;
  private final Instant submittedAt;
  private ClaimStatus status;
  private String assigneeId;
  private String decisionReason;
  private Instant updatedAt;
  private final Long version;

  private Claim(ClaimSnapshot snapshot) {
    id = snapshot.id();
    claimantId = snapshot.claimantId();
    claimType = snapshot.claimType();
    market = snapshot.market();
    incidentAt = snapshot.incidentAt();
    description = snapshot.description();
    estimatedLoss = snapshot.estimatedLoss();
    currency = snapshot.currency();
    status = snapshot.status();
    assigneeId = snapshot.assigneeId();
    decisionReason = snapshot.decisionReason();
    submittedAt = snapshot.submittedAt();
    updatedAt = snapshot.updatedAt();
    version = snapshot.version();
  }

  public static Claim submit(ClaimSubmission submission, Instant now) {
    Objects.requireNonNull(submission, "submission");
    Objects.requireNonNull(now, "now");

    var claimantId = required(submission.claimantId(), "claimant_required");
    if (submission.claimType() == null) {
      throw new DomainRuleException("claim_type_required");
    }
    var market = upperCode(submission.market(), 2, "market_invalid");
    if (submission.incidentAt() == null || submission.incidentAt().isAfter(now)) {
      throw new DomainRuleException("incident_time_invalid");
    }
    var description = normalize(submission.description());
    if (description == null || description.length() < 10 || description.length() > 2000) {
      throw new DomainRuleException("description_invalid");
    }
    var estimatedLoss = submission.estimatedLoss();
    if (estimatedLoss == null
        || estimatedLoss.compareTo(BigDecimal.ZERO) <= 0
        || estimatedLoss.stripTrailingZeros().scale() > 2) {
      throw new DomainRuleException("estimated_loss_invalid");
    }
    estimatedLoss = estimatedLoss.setScale(2);
    var currency = upperCode(submission.currency(), 3, "currency_invalid");

    return new Claim(
        new ClaimSnapshot(
            UUID.randomUUID(),
            claimantId,
            submission.claimType(),
            market,
            submission.incidentAt(),
            description,
            estimatedLoss,
            currency,
            ClaimStatus.SUBMITTED,
            null,
            null,
            now,
            now,
            null));
  }

  public static Claim rehydrate(ClaimSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");
    if (snapshot.version() == null) {
      throw new IllegalArgumentException("stored claim version is required");
    }
    return new Claim(snapshot);
  }

  public ClaimChange assign(String officerId, Instant now) {
    var normalizedOfficer = required(officerId, "officer_required");
    Objects.requireNonNull(now, "now");
    ensureOpen();
    if (assigneeId != null) {
      throw new DomainRuleException("claim_already_assigned");
    }
    if (status != ClaimStatus.SUBMITTED) {
      throw new DomainRuleException("claim_transition_invalid");
    }
    assigneeId = normalizedOfficer;
    updatedAt = now;
    return new ClaimChange("claim_assigned", status, normalizedOfficer, null, now);
  }

  public ClaimChange apply(ClaimAction action, String officerId, String reason, Instant now) {
    ensureOpen();
    Objects.requireNonNull(now, "now");
    var normalizedOfficer = required(officerId, "officer_required");
    requireAssignedOfficer(normalizedOfficer);
    if (action == null) {
      throw new DomainRuleException("claim_transition_invalid");
    }

    var transition = transitionFor(action, reason);
    status = transition.status();
    updatedAt = now;
    if (status.isClosed()) {
      decisionReason = transition.detail();
    }
    return new ClaimChange(
        transition.eventType(), status, normalizedOfficer, transition.detail(), now);
  }

  public ClaimChange provideInformation(String claimantId, String information, Instant now) {
    ensureOpen();
    Objects.requireNonNull(now, "now");
    if (status != ClaimStatus.MORE_INFORMATION_REQUIRED) {
      throw new DomainRuleException("claim_transition_invalid");
    }
    var normalizedClaimant = normalize(claimantId);
    if (!this.claimantId.equals(normalizedClaimant)) {
      throw new DomainRuleException("claimant_mismatch");
    }
    var normalizedInformation = required(information, "information_required");
    updatedAt = now;
    return new ClaimChange(
        "additional_information_provided", status, normalizedClaimant, normalizedInformation, now);
  }

  public ClaimSnapshot snapshot() {
    return new ClaimSnapshot(
        id,
        claimantId,
        claimType,
        market,
        incidentAt,
        description,
        estimatedLoss,
        currency,
        status,
        assigneeId,
        decisionReason,
        submittedAt,
        updatedAt,
        version);
  }

  private void ensureOpen() {
    if (status.isClosed()) {
      throw new DomainRuleException("claim_closed");
    }
  }

  private void requireAssignedOfficer(String officerId) {
    if (assigneeId == null || !assigneeId.equals(officerId)) {
      throw new DomainRuleException("claim_officer_mismatch");
    }
  }

  private Transition transitionFor(ClaimAction action, String reason) {
    return switch (status) {
      case SUBMITTED ->
          action == ClaimAction.START_REVIEW
              ? new Transition(ClaimStatus.UNDER_REVIEW, "review_started", null)
              : invalidTransition();
      case UNDER_REVIEW -> underReviewTransition(action, reason);
      case MORE_INFORMATION_REQUIRED ->
          action == ClaimAction.RESUME_REVIEW
              ? new Transition(ClaimStatus.UNDER_REVIEW, "review_resumed", null)
              : invalidTransition();
      case APPROVED, REJECTED -> throw new DomainRuleException("claim_closed");
    };
  }

  private Transition underReviewTransition(ClaimAction action, String reason) {
    return switch (action) {
      case REQUEST_MORE_INFORMATION ->
          new Transition(
              ClaimStatus.MORE_INFORMATION_REQUIRED,
              "more_information_requested",
              required(reason, "reason_required"));
      case APPROVE ->
          new Transition(
              ClaimStatus.APPROVED, "claim_approved", required(reason, "reason_required"));
      case REJECT ->
          new Transition(
              ClaimStatus.REJECTED, "claim_rejected", required(reason, "reason_required"));
      default -> invalidTransition();
    };
  }

  private static Transition invalidTransition() {
    throw new DomainRuleException("claim_transition_invalid");
  }

  private static String required(String value, String code) {
    var normalized = normalize(value);
    if (normalized == null) {
      throw new DomainRuleException(code);
    }
    return normalized;
  }

  private static String upperCode(String value, int length, String code) {
    var normalized = normalize(value);
    if (normalized == null
        || normalized.length() != length
        || !normalized.chars().allMatch(Character::isLetter)) {
      throw new DomainRuleException(code);
    }
    return normalized.toUpperCase(Locale.ROOT);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private record Transition(ClaimStatus status, String eventType, String detail) {}
}
