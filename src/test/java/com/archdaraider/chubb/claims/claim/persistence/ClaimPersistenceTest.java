package com.archdaraider.chubb.claims.claim.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archdaraider.chubb.claims.claim.application.ClaimEvidenceStore;
import com.archdaraider.chubb.claims.claim.application.ClaimStore;
import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimSubmission;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@ActiveProfiles("test")
class ClaimPersistenceTest {
  private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

  @Autowired private JdbcClient jdbc;
  @Autowired private ClaimStore claimStore;
  @Autowired private ClaimEvidenceStore evidenceStore;
  @Autowired private JsonMapper jsonMapper;

  @BeforeEach
  void clearDatabase() {
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
  }

  @Test
  void migrationCreatesClaimEvidenceTables() {
    assertThat(rowCount("claims")).isZero();
    assertThat(rowCount("claim_timeline")).isZero();
    assertThat(rowCount("outbox_messages")).isZero();
  }

  @Test
  void savesAndReloadsEverySnapshotField() {
    var claim = newClaim("claimant-101", "sg", "2500.50", "sgd");
    claim.assign("officer-7", NOW.plusSeconds(1));
    claim.apply(ClaimAction.START_REVIEW, "officer-7", null, NOW.plusSeconds(2));
    claim.apply(ClaimAction.APPROVE, "officer-7", "covered", NOW.plusSeconds(3));
    var before = claim.snapshot();

    var saved = claimStore.save(claim);
    var loaded = claimStore.findById(before.id()).orElseThrow();

    assertThat(saved.snapshot())
        .usingRecursiveComparison()
        .ignoringFields("version")
        .isEqualTo(before);
    assertThat(saved.snapshot().version()).isZero();
    assertThat(loaded.snapshot()).isEqualTo(saved.snapshot());
  }

  @Test
  void appendsOrderedTimelineAndValidOutboxJson() throws Exception {
    var claim = claimStore.save(newClaim("claimant-101", "SG", "2500", "SGD"));
    var later =
        new ClaimChange(
            "review_started", ClaimStatus.UNDER_REVIEW, "officer-7", null, NOW.plusSeconds(2));
    var earlier =
        new ClaimChange(
            "claim_assigned", ClaimStatus.SUBMITTED, "officer-7", null, NOW.plusSeconds(1));

    evidenceStore.append(claim.snapshot().id(), later);
    evidenceStore.append(claim.snapshot().id(), earlier);

    assertThat(evidenceStore.findTimeline(claim.snapshot().id()))
        .extracting(item -> item.eventType())
        .containsExactly("claim_assigned", "review_started");
    var payloads =
        jdbc.sql("select payload from outbox_messages order by occurred_at")
            .query(String.class)
            .list();
    assertThat(payloads).hasSize(2);
    var payload = jsonMapper.readTree(payloads.getFirst());
    assertThat(payload.get("claimId").asString()).isEqualTo(claim.snapshot().id().toString());
    assertThat(payload.get("eventType").asString()).isEqualTo("claim_assigned");
    assertThat(payload.get("status").asString()).isEqualTo("submitted");
    assertThat(payload.get("occurredAt").asString()).isEqualTo(NOW.plusSeconds(1).toString());
  }

  @Test
  void filtersTheOpenWorkQueueByStatusAndAssignee() {
    var submitted = claimStore.save(newClaim("c-1", "SG", "100", "SGD"));
    var underReview = newClaim("c-2", "SG", "200", "SGD");
    underReview.assign("officer-7", NOW.plusSeconds(1));
    underReview.apply(ClaimAction.START_REVIEW, "officer-7", null, NOW.plusSeconds(2));
    underReview = claimStore.save(underReview);
    var waiting = newClaim("c-3", "SG", "300", "SGD");
    waiting.assign("officer-8", NOW.plusSeconds(1));
    waiting.apply(ClaimAction.START_REVIEW, "officer-8", null, NOW.plusSeconds(2));
    waiting.apply(
        ClaimAction.REQUEST_MORE_INFORMATION, "officer-8", "send invoice", NOW.plusSeconds(3));
    waiting = claimStore.save(waiting);
    var approved = newClaim("c-4", "SG", "400", "SGD");
    approved.assign("officer-7", NOW.plusSeconds(1));
    approved.apply(ClaimAction.START_REVIEW, "officer-7", null, NOW.plusSeconds(2));
    approved.apply(ClaimAction.APPROVE, "officer-7", "covered", NOW.plusSeconds(3));
    claimStore.save(approved);

    assertThat(claimStore.findForQueue(null, null))
        .extracting(item -> item.snapshot().id())
        .containsExactlyInAnyOrder(
            submitted.snapshot().id(), underReview.snapshot().id(), waiting.snapshot().id());
    assertThat(claimStore.findForQueue(ClaimStatus.UNDER_REVIEW, null))
        .extracting(item -> item.snapshot().id())
        .containsExactly(underReview.snapshot().id());
    assertThat(claimStore.findForQueue(null, "officer-7"))
        .extracting(item -> item.snapshot().id())
        .containsExactly(underReview.snapshot().id());
    assertThat(claimStore.findForQueue(ClaimStatus.MORE_INFORMATION_REQUIRED, "officer-8"))
        .extracting(item -> item.snapshot().id())
        .containsExactly(waiting.snapshot().id());
  }

  @Test
  void findsOnlyOpenClaimsInTheRequestedMarket() {
    var sgOpen = claimStore.save(newClaim("c-1", "sg", "100", "SGD"));
    claimStore.save(newClaim("c-2", "au", "200", "AUD"));
    var sgClosed = newClaim("c-3", "sg", "300", "SGD");
    sgClosed.assign("officer-7", NOW.plusSeconds(1));
    sgClosed.apply(ClaimAction.START_REVIEW, "officer-7", null, NOW.plusSeconds(2));
    sgClosed.apply(ClaimAction.REJECT, "officer-7", "not covered", NOW.plusSeconds(3));
    claimStore.save(sgClosed);

    assertThat(claimStore.findOpenByMarket("sg"))
        .extracting(item -> item.snapshot().id())
        .containsExactly(sgOpen.snapshot().id());
  }

  @Test
  void staleSaveIsRejected() {
    var saved = claimStore.save(newClaim("claimant-101", "SG", "2500", "SGD"));
    var first = claimStore.findById(saved.snapshot().id()).orElseThrow();
    var second = claimStore.findById(saved.snapshot().id()).orElseThrow();
    first.assign("officer-7", NOW.plusSeconds(1));
    second.assign("officer-8", NOW.plusSeconds(1));

    claimStore.save(first);

    assertThatThrownBy(() -> claimStore.save(second))
        .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    assertThat(claimStore.findById(saved.snapshot().id()).orElseThrow().snapshot().assigneeId())
        .isEqualTo("officer-7");
  }

  private long rowCount(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  private Claim newClaim(String claimantId, String market, String estimatedLoss, String currency) {
    return Claim.submit(
        new ClaimSubmission(
            claimantId,
            ClaimType.MOTOR,
            market,
            NOW.minusSeconds(60),
            "Rear bumper was damaged",
            new BigDecimal(estimatedLoss),
            currency),
        NOW);
  }
}
