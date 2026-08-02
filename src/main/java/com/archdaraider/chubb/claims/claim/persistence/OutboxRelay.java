package com.archdaraider.chubb.claims.claim.persistence;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {
  private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelay.class);

  private final OutboxJpaRepository repository;
  private final Clock clock;

  OutboxRelay(OutboxJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional
  public int publishPending() {
    var pending = repository.findTop100ByProcessedAtIsNullOrderByOccurredAtAsc();
    var processedAt = clock.instant();
    for (var message : pending) {
      LOGGER.info(
          "outbox event claimId={} eventType={} occurredAt={}",
          message.claimId(),
          message.eventType(),
          message.occurredAt());
      message.markProcessed(processedAt);
    }
    return pending.size();
  }
}
