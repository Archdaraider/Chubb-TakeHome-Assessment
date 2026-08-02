package com.archdaraider.chubb.claims.claim.persistence;

import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ClaimJpaRepository extends JpaRepository<ClaimEntity, UUID> {
  List<ClaimEntity> findByStatusOrderBySubmittedAtAsc(ClaimStatus status);

  List<ClaimEntity> findByAssigneeIdOrderBySubmittedAtAsc(String assigneeId);

  List<ClaimEntity> findByStatusInOrderBySubmittedAtAsc(Collection<ClaimStatus> statuses);

  List<ClaimEntity> findByMarketAndStatusInOrderBySubmittedAtAsc(
      String market, Collection<ClaimStatus> statuses);
}
