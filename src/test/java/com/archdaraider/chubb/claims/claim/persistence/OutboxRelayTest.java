package com.archdaraider.chubb.claims.claim.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.archdaraider.chubb.claims.claim.application.ClaimEvidenceStore;
import com.archdaraider.chubb.claims.claim.application.ClaimStore;
import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimSubmission;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import(OutboxRelayTest.RelayTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayTest {
  private static final Instant OCCURRED_AT = Instant.parse("2026-08-03T01:00:00Z");
  private static final Instant PROCESSED_AT = Instant.parse("2026-08-03T02:00:00Z");

  @Autowired private OutboxRelay relay;
  @Autowired private OutboxJpaRepository outboxRepository;
  @Autowired private ClaimStore claimStore;
  @Autowired private ClaimEvidenceStore evidenceStore;
  @Autowired private JdbcClient jdbc;
  @Autowired private TransactionTemplate transactions;

  @BeforeEach
  void clearDatabase() {
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
  }

  @Test
  void publishPendingStampsAndLogsPendingRowsOnce(CapturedOutput output) {
    var claim = claimStore.save(newClaim());
    evidenceStore.append(
        claim.snapshot().id(), change("claim_assigned", OCCURRED_AT.plusSeconds(2)));
    evidenceStore.append(claim.snapshot().id(), change("claim_submitted", OCCURRED_AT));

    assertThat(relay.publishPending()).isEqualTo(2);
    assertThat(processedTimes()).containsExactly(PROCESSED_AT, PROCESSED_AT);
    assertThat(output.getOut())
        .containsSubsequence(
            "claimId=" + claim.snapshot().id(),
            "eventType=claim_submitted",
            "occurredAt=" + OCCURRED_AT,
            "claimId=" + claim.snapshot().id(),
            "eventType=claim_assigned",
            "occurredAt=" + OCCURRED_AT.plusSeconds(2));
    assertThat(relay.publishPending()).isZero();
  }

  @Test
  void publishPendingLimitsTheBatchToTheOldestOneHundred() {
    var claim = claimStore.save(newClaim());
    for (int index = 0; index < 101; index++) {
      outboxRepository.save(
          new OutboxEntity(
              UUID.randomUUID(),
              claim.snapshot().id(),
              "event_" + index,
              "{}",
              OCCURRED_AT.plusSeconds(index)));
    }

    assertThat(relay.publishPending()).isEqualTo(100);
    assertThat(pendingEventTypes()).containsExactly("event_100");
    assertThat(relay.publishPending()).isOne();
    assertThat(relay.publishPending()).isZero();
  }

  @Test
  void rolledBackRowsAreNeverPublished() {
    var claim = claimStore.save(newClaim());
    transactions.executeWithoutResult(
        status -> {
          evidenceStore.append(claim.snapshot().id(), change("claim_assigned", OCCURRED_AT));
          status.setRollbackOnly();
        });

    assertThat(rowCount("outbox_messages")).isZero();
    assertThat(relay.publishPending()).isZero();
  }

  private Claim newClaim() {
    return Claim.submit(
        new ClaimSubmission(
            "claimant-101",
            ClaimType.MOTOR,
            "SG",
            OCCURRED_AT.minusSeconds(60),
            "Rear bumper was damaged",
            new BigDecimal("2500.00"),
            "SGD"),
        OCCURRED_AT);
  }

  private ClaimChange change(String eventType, Instant occurredAt) {
    return new ClaimChange(eventType, ClaimStatus.SUBMITTED, "officer-7", null, occurredAt);
  }

  private java.util.List<Instant> processedTimes() {
    return jdbc.sql("select processed_at from outbox_messages order by occurred_at")
        .query(Instant.class)
        .list();
  }

  private java.util.List<String> pendingEventTypes() {
    return jdbc.sql(
            "select event_type from outbox_messages where processed_at is null order by occurred_at")
        .query(String.class)
        .list();
  }

  private long rowCount(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RelayTestConfiguration {
    @Bean
    @Primary
    Clock fixedRelayClock() {
      return Clock.fixed(PROCESSED_AT, ZoneOffset.UTC);
    }
  }
}
