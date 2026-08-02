package com.archdaraider.chubb.claims.claim.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    name = "claims.outbox.relay.enabled",
    havingValue = "true",
    matchIfMissing = true)
class OutboxRelayScheduler {
  private final OutboxRelay relay;

  OutboxRelayScheduler(OutboxRelay relay) {
    this.relay = relay;
  }

  @Scheduled(fixedDelayString = "${claims.outbox.relay.fixed-delay-ms:5000}")
  void publishPending() {
    relay.publishPending();
  }
}
