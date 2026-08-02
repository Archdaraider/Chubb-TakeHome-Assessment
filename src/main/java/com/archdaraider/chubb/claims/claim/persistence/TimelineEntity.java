package com.archdaraider.chubb.claims.claim.persistence;

import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_timeline")
class TimelineEntity {
  @Id private UUID id;

  @Column(name = "claim_id", nullable = false)
  private UUID claimId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(name = "resulting_status", nullable = false, length = 40)
  private ClaimStatus resultingStatus;

  @Column(name = "actor_id", length = 100)
  private String actorId;

  @Lob private String detail;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected TimelineEntity() {}

  TimelineEntity(
      UUID id,
      UUID claimId,
      String eventType,
      ClaimStatus resultingStatus,
      String actorId,
      String detail,
      Instant occurredAt) {
    this.id = id;
    this.claimId = claimId;
    this.eventType = eventType;
    this.resultingStatus = resultingStatus;
    this.actorId = actorId;
    this.detail = detail;
    this.occurredAt = occurredAt;
  }

  String eventType() {
    return eventType;
  }

  ClaimStatus resultingStatus() {
    return resultingStatus;
  }

  String actorId() {
    return actorId;
  }

  String detail() {
    return detail;
  }

  Instant occurredAt() {
    return occurredAt;
  }
}
