package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import java.util.List;
import java.util.UUID;

public interface ClaimEvidenceStore {
  void append(UUID claimId, ClaimChange change);

  List<TimelineItem> findTimeline(UUID claimId);
}
