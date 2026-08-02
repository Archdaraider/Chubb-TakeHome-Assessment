package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import java.util.List;

public record ClaimDetails(ClaimSnapshot snapshot, List<TimelineItem> timeline) {
  public ClaimDetails {
    timeline = List.copyOf(timeline);
  }
}
