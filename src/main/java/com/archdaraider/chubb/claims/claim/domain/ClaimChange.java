package com.archdaraider.chubb.claims.claim.domain;

import java.time.Instant;

public record ClaimChange(
    String eventType,
    ClaimStatus resultingStatus,
    String actorId,
    String detail,
    Instant occurredAt) {}
