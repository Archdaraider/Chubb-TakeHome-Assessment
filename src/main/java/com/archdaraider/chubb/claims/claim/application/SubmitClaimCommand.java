package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;

public record SubmitClaimCommand(
    String claimantId,
    ClaimType claimType,
    String market,
    Instant incidentAt,
    String description,
    BigDecimal estimatedLoss,
    String currency) {}
