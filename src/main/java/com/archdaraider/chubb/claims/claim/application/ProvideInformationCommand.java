package com.archdaraider.chubb.claims.claim.application;

import java.util.UUID;

public record ProvideInformationCommand(UUID claimId, String claimantId, String information) {}
