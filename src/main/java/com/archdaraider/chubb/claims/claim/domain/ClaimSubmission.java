package com.archdaraider.chubb.claims.claim.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record ClaimSubmission(
    String claimantId,
    ClaimType claimType,
    String market,
    Instant incidentAt,
    String description,
    BigDecimal estimatedLoss,
    String currency) {}
