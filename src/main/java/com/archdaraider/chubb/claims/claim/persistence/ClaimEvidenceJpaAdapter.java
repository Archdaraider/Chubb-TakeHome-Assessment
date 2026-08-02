package com.archdaraider.chubb.claims.claim.persistence;

import com.archdaraider.chubb.claims.claim.application.ClaimEvidenceStore;
import com.archdaraider.chubb.claims.claim.application.TimelineItem;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class ClaimEvidenceJpaAdapter implements ClaimEvidenceStore {
  private final TimelineJpaRepository timelineRepository;
  private final OutboxJpaRepository outboxRepository;
  private final JsonMapper jsonMapper;

  ClaimEvidenceJpaAdapter(
      TimelineJpaRepository timelineRepository,
      OutboxJpaRepository outboxRepository,
      JsonMapper jsonMapper) {
    this.timelineRepository = timelineRepository;
    this.outboxRepository = outboxRepository;
    this.jsonMapper = jsonMapper;
  }

  @Override
  @Transactional
  public void append(UUID claimId, ClaimChange change) {
    timelineRepository.save(
        new TimelineEntity(
            UUID.randomUUID(),
            claimId,
            change.eventType(),
            change.resultingStatus(),
            change.actorId(),
            change.detail(),
            change.occurredAt()));
    outboxRepository.save(
        new OutboxEntity(
            UUID.randomUUID(),
            claimId,
            change.eventType(),
            serializePayload(claimId, change),
            change.occurredAt()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TimelineItem> findTimeline(UUID claimId) {
    return timelineRepository.findByClaimIdOrderByOccurredAtAsc(claimId).stream()
        .map(
            entity ->
                new TimelineItem(
                    entity.eventType(),
                    entity.resultingStatus(),
                    entity.actorId(),
                    entity.detail(),
                    entity.occurredAt()))
        .toList();
  }

  private String serializePayload(UUID claimId, ClaimChange change) {
    var payload = new LinkedHashMap<String, String>();
    payload.put("claimId", claimId.toString());
    payload.put("eventType", change.eventType());
    payload.put("status", change.resultingStatus().value());
    payload.put("occurredAt", change.occurredAt().toString());
    try {
      return jsonMapper.writeValueAsString(payload);
    } catch (Exception exception) {
      throw new IllegalStateException("outbox_payload_invalid", exception);
    }
  }
}
