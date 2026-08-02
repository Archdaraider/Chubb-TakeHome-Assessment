package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import java.time.Instant;

public record TimelineItem(
    String eventType,
    ClaimStatus resultingStatus,
    String actorId,
    String detail,
    Instant occurredAt) {}
