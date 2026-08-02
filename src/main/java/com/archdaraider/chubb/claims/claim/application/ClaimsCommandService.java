package com.archdaraider.chubb.claims.claim.application;

import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import com.archdaraider.chubb.claims.claim.domain.ClaimSubmission;
import java.time.Clock;
import java.util.UUID;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimsCommandService {
  private final ClaimStore claimStore;
  private final ClaimEvidenceStore evidenceStore;
  private final Clock clock;

  public ClaimsCommandService(
      ClaimStore claimStore, ClaimEvidenceStore evidenceStore, Clock clock) {
    this.claimStore = claimStore;
    this.evidenceStore = evidenceStore;
    this.clock = clock;
  }

  @Transactional
  public ClaimSnapshot submit(SubmitClaimCommand command) {
    var now = clock.instant();
    var claim =
        Claim.submit(
            new ClaimSubmission(
                command.claimantId(),
                command.claimType(),
                command.market(),
                command.incidentAt(),
                command.description(),
                command.estimatedLoss(),
                command.currency()),
            now);
    var change =
        new ClaimChange(
            "claim_submitted", claim.snapshot().status(), claim.snapshot().claimantId(), null, now);
    return saveWithEvidence(claim, change);
  }

  @Transactional
  public ClaimSnapshot assign(AssignClaimCommand command) {
    var claim = find(command.claimId());
    var change = claim.assign(command.officerId(), clock.instant());
    return saveWithEvidence(claim, change);
  }

  @Transactional
  public ClaimSnapshot apply(ApplyClaimActionCommand command) {
    var claim = find(command.claimId());
    var change =
        claim.apply(command.action(), command.officerId(), command.reason(), clock.instant());
    return saveWithEvidence(claim, change);
  }

  @Transactional
  public ClaimSnapshot provideInformation(ProvideInformationCommand command) {
    var claim = find(command.claimId());
    var change =
        claim.provideInformation(command.claimantId(), command.information(), clock.instant());
    return saveWithEvidence(claim, change);
  }

  private Claim find(UUID claimId) {
    return claimStore
        .findById(claimId)
        .orElseThrow(
            () -> new ClaimNotFoundException("claim_not_found", "the claim was not found"));
  }

  private ClaimSnapshot saveWithEvidence(Claim claim, ClaimChange change) {
    try {
      var saved = claimStore.save(claim);
      evidenceStore.append(saved.snapshot().id(), change);
      return saved.snapshot();
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new ClaimConflictException(
          "claim_conflict", "the claim changed; reload and try again", exception);
    }
  }
}
