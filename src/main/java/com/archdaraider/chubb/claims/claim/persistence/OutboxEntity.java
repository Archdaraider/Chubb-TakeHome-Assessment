package com.archdaraider.chubb.claims.claim.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_messages")
class OutboxEntity {
  @Id private UUID id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Lob
  @Column(nullable = false)
  private String payload;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  protected OutboxEntity() {}

  OutboxEntity(UUID id, UUID claimId, String eventType, String payload, Instant occurredAt) {
    this.id = id;
    this.claimId = claimId;
    this.eventType = eventType;
    this.payload = payload;
    this.occurredAt = occurredAt;
  }

  UUID claimId() {
    return claimId;
  }

  String eventType() {
    return eventType;
  }

  Instant occurredAt() {
    return occurredAt;
  }

  void markProcessed(Instant processedAt) {
    this.processedAt = Objects.requireNonNull(processedAt);
  }
}
