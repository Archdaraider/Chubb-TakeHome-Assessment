package com.archdaraider.chubb.claims.claim.persistence;

import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claims")
class ClaimEntity {
  @Id private UUID id;

  @Column(name = "claimant_id", nullable = false, length = 100)
  private String claimantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "claim_type", nullable = false, length = 30)
  private ClaimType claimType;

  @Column(nullable = false, length = 2)
  private String market;

  @Column(name = "incident_at", nullable = false)
  private Instant incidentAt;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(name = "estimated_loss", nullable = false, precision = 19, scale = 2)
  private BigDecimal estimatedLoss;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ClaimStatus status;

  @Column(name = "assignee_id", length = 100)
  private String assigneeId;

  @Column(name = "decision_reason", length = 2000)
  private String decisionReason;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  protected ClaimEntity() {}

  ClaimEntity(
      UUID id,
      String claimantId,
      ClaimType claimType,
      String market,
      Instant incidentAt,
      String description,
      BigDecimal estimatedLoss,
      String currency,
      ClaimStatus status,
      String assigneeId,
      String decisionReason,
      Instant submittedAt,
      Instant updatedAt,
      Long version) {
    this.id = id;
    this.claimantId = claimantId;
    this.claimType = claimType;
    this.market = market;
    this.incidentAt = incidentAt;
    this.description = description;
    this.estimatedLoss = estimatedLoss;
    this.currency = currency;
    this.status = status;
    this.assigneeId = assigneeId;
    this.decisionReason = decisionReason;
    this.submittedAt = submittedAt;
    this.updatedAt = updatedAt;
    this.version = version;
  }

  UUID id() {
    return id;
  }

  String claimantId() {
    return claimantId;
  }

  ClaimType claimType() {
    return claimType;
  }

  String market() {
    return market;
  }

  Instant incidentAt() {
    return incidentAt;
  }

  String description() {
    return description;
  }

  BigDecimal estimatedLoss() {
    return estimatedLoss;
  }

  String currency() {
    return currency;
  }

  ClaimStatus status() {
    return status;
  }

  String assigneeId() {
    return assigneeId;
  }

  String decisionReason() {
    return decisionReason;
  }

  Instant submittedAt() {
    return submittedAt;
  }

  Instant updatedAt() {
    return updatedAt;
  }

  Long version() {
    return version;
  }
}
