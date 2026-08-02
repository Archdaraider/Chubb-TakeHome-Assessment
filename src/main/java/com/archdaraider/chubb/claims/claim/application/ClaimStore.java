package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimStore {
  Optional<Claim> findById(UUID claimId);

  Claim save(Claim claim);

  List<Claim> findForQueue(ClaimStatus status, String assigneeId);

  List<Claim> findOpenByMarket(String market);

  boolean hasAny();
}
