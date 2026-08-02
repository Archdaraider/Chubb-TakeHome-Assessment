package com.archdaraider.chubb.claims.claim.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TimelineJpaRepository extends JpaRepository<TimelineEntity, UUID> {
  List<TimelineEntity> findByClaimIdOrderByOccurredAtAsc(UUID claimId);
}
