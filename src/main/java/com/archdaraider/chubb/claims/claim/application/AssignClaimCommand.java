package com.archdaraider.chubb.claims.claim.application;

import java.util.UUID;

public record AssignClaimCommand(UUID claimId, String officerId) {}
