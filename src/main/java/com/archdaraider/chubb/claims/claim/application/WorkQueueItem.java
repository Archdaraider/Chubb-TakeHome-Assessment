package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkQueueItem(
    UUID claimId,
    String claimantId,
    ClaimType claimType,
    String market,
    ClaimStatus status,
    String assigneeId,
    BigDecimal estimatedLoss,
    String currency,
    Instant submittedAt,
    Instant updatedAt) {}
