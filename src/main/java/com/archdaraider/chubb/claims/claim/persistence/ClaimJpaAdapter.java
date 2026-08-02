package com.archdaraider.chubb.claims.claim.persistence;

import com.archdaraider.chubb.claims.claim.application.ClaimStore;
import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import jakarta.persistence.EntityManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ClaimJpaAdapter implements ClaimStore {
  private static final EnumSet<ClaimStatus> OPEN_STATUSES =
      EnumSet.of(
          ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, ClaimStatus.MORE_INFORMATION_REQUIRED);

  private final EntityManager entityManager;
  private final ClaimJpaRepository repository;

  ClaimJpaAdapter(EntityManager entityManager, ClaimJpaRepository repository) {
    this.entityManager = entityManager;
    this.repository = repository;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Claim> findById(UUID claimId) {
    return repository.findById(claimId).map(ClaimJpaAdapter::toDomain);
  }

  @Override
  @Transactional
  public Claim save(Claim claim) {
    var entity = fromSnapshot(claim.snapshot());
    ClaimEntity managed;
    if (claim.snapshot().version() == null) {
      entityManager.persist(entity);
      managed = entity;
    } else {
      managed = entityManager.merge(entity);
    }
    entityManager.flush();
    return toDomain(managed);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Claim> findForQueue(ClaimStatus status, String assigneeId) {
    if (status != null && !OPEN_STATUSES.contains(status)) {
      return List.of();
    }

    var normalizedAssignee = normalize(assigneeId);
    List<ClaimEntity> entities;
    if (status != null) {
      entities = repository.findByStatusOrderBySubmittedAtAsc(status);
    } else if (normalizedAssignee != null) {
      entities = repository.findByAssigneeIdOrderBySubmittedAtAsc(normalizedAssignee);
    } else {
      entities = repository.findByStatusInOrderBySubmittedAtAsc(OPEN_STATUSES);
    }

    return entities.stream()
        .filter(entity -> OPEN_STATUSES.contains(entity.status()))
        .filter(
            entity -> normalizedAssignee == null || normalizedAssignee.equals(entity.assigneeId()))
        .map(ClaimJpaAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Claim> findOpenByMarket(String market) {
    var normalizedMarket = normalize(market);
    if (normalizedMarket == null) {
      return repository.findByStatusInOrderBySubmittedAtAsc(OPEN_STATUSES).stream()
          .map(ClaimJpaAdapter::toDomain)
          .toList();
    }
    return repository
        .findByMarketAndStatusInOrderBySubmittedAtAsc(
            normalizedMarket.toUpperCase(Locale.ROOT), OPEN_STATUSES)
        .stream()
        .map(ClaimJpaAdapter::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasAny() {
    return repository.count() > 0;
  }

  private static ClaimEntity fromSnapshot(ClaimSnapshot snapshot) {
    return new ClaimEntity(
        snapshot.id(),
        snapshot.claimantId(),
        snapshot.claimType(),
        snapshot.market(),
        snapshot.incidentAt(),
        snapshot.description(),
        snapshot.estimatedLoss(),
        snapshot.currency(),
        snapshot.status(),
        snapshot.assigneeId(),
        snapshot.decisionReason(),
        snapshot.submittedAt(),
        snapshot.updatedAt(),
        snapshot.version());
  }

  private static Claim toDomain(ClaimEntity entity) {
    return Claim.rehydrate(
        new ClaimSnapshot(
            entity.id(),
            entity.claimantId(),
            entity.claimType(),
            entity.market(),
            entity.incidentAt(),
            entity.description(),
            entity.estimatedLoss(),
            entity.currency(),
            entity.status(),
            entity.assigneeId(),
            entity.decisionReason(),
            entity.submittedAt(),
            entity.updatedAt(),
            entity.version()));
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
