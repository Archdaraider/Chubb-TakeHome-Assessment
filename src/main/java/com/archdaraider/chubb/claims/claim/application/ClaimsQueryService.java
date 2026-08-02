package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClaimsQueryService {
  private final ClaimStore claimStore;
  private final ClaimEvidenceStore evidenceStore;

  public ClaimsQueryService(ClaimStore claimStore, ClaimEvidenceStore evidenceStore) {
    this.claimStore = claimStore;
    this.evidenceStore = evidenceStore;
  }

  public ClaimDetails get(UUID claimId) {
    var snapshot =
        claimStore
            .findById(claimId)
            .map(Claim::snapshot)
            .orElseThrow(
                () -> new ClaimNotFoundException("claim_not_found", "the claim was not found"));
    return new ClaimDetails(snapshot, evidenceStore.findTimeline(claimId));
  }

  public List<WorkQueueItem> workQueue(ClaimStatus status, String assigneeId) {
    return claimStore.findForQueue(status, assigneeId).stream()
        .map(Claim::snapshot)
        .sorted(Comparator.comparing(ClaimSnapshot::submittedAt).thenComparing(ClaimSnapshot::id))
        .map(ClaimsQueryService::toQueueItem)
        .toList();
  }

  public List<ExposureItem> exposure(String market) {
    Map<String, List<Claim>> byCurrency =
        claimStore.findOpenByMarket(market).stream()
            .collect(
                Collectors.groupingBy(
                    claim -> claim.snapshot().currency(), TreeMap::new, Collectors.toList()));

    return byCurrency.entrySet().stream()
        .map(
            entry ->
                new ExposureItem(
                    entry.getKey(),
                    entry.getValue().stream()
                        .map(claim -> claim.snapshot().estimatedLoss())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                    entry.getValue().size()))
        .toList();
  }

  private static WorkQueueItem toQueueItem(ClaimSnapshot snapshot) {
    return new WorkQueueItem(
        snapshot.id(),
        snapshot.claimantId(),
        snapshot.claimType(),
        snapshot.market(),
        snapshot.status(),
        snapshot.assigneeId(),
        snapshot.estimatedLoss(),
        snapshot.currency(),
        snapshot.submittedAt(),
        snapshot.updatedAt());
  }
}
