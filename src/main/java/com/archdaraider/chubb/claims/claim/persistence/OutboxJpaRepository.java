package com.archdaraider.chubb.claims.claim.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxJpaRepository extends JpaRepository<OutboxEntity, UUID> {}
