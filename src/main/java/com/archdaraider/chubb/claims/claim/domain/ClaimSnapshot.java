package com.archdaraider.chubb.claims.claim.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClaimSnapshot(
    UUID id,
    String claimantId,
    ClaimType claimType,
    String market,
    Instant incidentAt,
    String description,
    BigDecimal estimatedLoss,
    String currency,
    ClaimStatus status,
    String assigneeId,
    String decisionReason,
    Instant submittedAt,
    Instant updatedAt,
    Long version) {}
