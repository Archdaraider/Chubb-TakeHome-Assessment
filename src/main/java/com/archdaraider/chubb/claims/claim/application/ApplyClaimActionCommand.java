package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import java.util.UUID;

public record ApplyClaimActionCommand(
    UUID claimId, ClaimAction action, String officerId, String reason) {}
