package com.archdaraider.chubb.claims.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimSubmission;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClaimsQueryServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

  @Autowired private ClaimsQueryService service;
  @Autowired private ClaimStore claimStore;
  @Autowired private ClaimEvidenceStore evidenceStore;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  void clearDatabase() {
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
  }

  @Test
  void getReturnsSnapshotAndTimelineInOccurrenceOrder() {
    var claim = saveClaim("c-1", "SG", "100", "SGD", ClaimStatus.SUBMITTED, null, 0);
    evidenceStore.append(
        claim.snapshot().id(),
        new ClaimChange(
            "later_event", ClaimStatus.SUBMITTED, "actor-2", "later", NOW.plusSeconds(20)));
    evidenceStore.append(
        claim.snapshot().id(),
        new ClaimChange(
            "earlier_event", ClaimStatus.SUBMITTED, "actor-1", "earlier", NOW.plusSeconds(10)));

    var details = service.get(claim.snapshot().id());

    assertThat(details.snapshot()).isEqualTo(claim.snapshot());
    assertThat(details.timeline())
        .extracting(TimelineItem::eventType)
        .containsExactly("earlier_event", "later_event");
  }

  @Test
  void missingGetReturnsStableCode() {
    assertThatThrownBy(() -> service.get(UUID.randomUUID()))
        .isInstanceOf(ClaimNotFoundException.class)
        .extracting(error -> ((ClaimNotFoundException) error).code())
        .isEqualTo("claim_not_found");
  }

  @Test
  void queueReturnsOnlyOpenClaims() {
    var data = seedQueueAndExposureClaims();

    assertThat(service.workQueue(null, null))
        .extracting(WorkQueueItem::claimId)
        .containsExactly(
            data.submitted().snapshot().id(),
            data.underReview().snapshot().id(),
            data.waiting().snapshot().id(),
            data.auOpen().snapshot().id());
  }

  @Test
  void queueFiltersStatusAssigneeAndBothTogether() {
    var data = seedQueueAndExposureClaims();

    assertThat(service.workQueue(ClaimStatus.UNDER_REVIEW, null))
        .extracting(WorkQueueItem::claimId)
        .containsExactly(data.underReview().snapshot().id());
    assertThat(service.workQueue(null, "officer-8"))
        .extracting(WorkQueueItem::claimId)
        .containsExactly(data.waiting().snapshot().id());
    assertThat(service.workQueue(ClaimStatus.MORE_INFORMATION_REQUIRED, "officer-8"))
        .extracting(WorkQueueItem::claimId)
        .containsExactly(data.waiting().snapshot().id());
    assertThat(service.workQueue(ClaimStatus.UNDER_REVIEW, "officer-8")).isEmpty();
  }

  @Test
  void exposureFiltersMarketOpenStateAndCurrency() {
    seedQueueAndExposureClaims();

    assertThat(service.exposure("sg"))
        .containsExactly(
            new ExposureItem("AUD", new BigDecimal("300.00"), 1),
            new ExposureItem("SGD", new BigDecimal("300.00"), 2));
  }

  @Test
  void unfilteredExposureCombinesMarketsWithinEachCurrency() {
    seedQueueAndExposureClaims();

    assertThat(service.exposure(null))
        .containsExactly(
            new ExposureItem("AUD", new BigDecimal("900.00"), 2),
            new ExposureItem("SGD", new BigDecimal("300.00"), 2));
  }

  @Test
  void approvingClaimReducesItsCurrencyExposure() {
    var data = seedQueueAndExposureClaims();
    var before = service.exposure("SG");

    data.underReview().apply(ClaimAction.APPROVE, "officer-7", "covered", NOW.plusSeconds(80));
    claimStore.save(data.underReview());

    assertThat(before).contains(new ExposureItem("SGD", new BigDecimal("300.00"), 2));
    assertThat(service.exposure("SG"))
        .containsExactly(
            new ExposureItem("AUD", new BigDecimal("300.00"), 1),
            new ExposureItem("SGD", new BigDecimal("100.00"), 1));
  }

  private SeedData seedQueueAndExposureClaims() {
    var submitted = saveClaim("c-1", "SG", "100.00", "SGD", ClaimStatus.SUBMITTED, null, 1);
    var underReview =
        saveClaim("c-2", "SG", "200.00", "SGD", ClaimStatus.UNDER_REVIEW, "officer-7", 2);
    var waiting =
        saveClaim(
            "c-3", "SG", "300.00", "AUD", ClaimStatus.MORE_INFORMATION_REQUIRED, "officer-8", 3);
    var approved = saveClaim("c-4", "SG", "400.00", "SGD", ClaimStatus.APPROVED, "officer-7", 4);
    var rejected = saveClaim("c-5", "SG", "500.00", "SGD", ClaimStatus.REJECTED, "officer-7", 5);
    var auOpen = saveClaim("c-6", "AU", "600.00", "AUD", ClaimStatus.SUBMITTED, null, 6);
    return new SeedData(submitted, underReview, waiting, approved, rejected, auOpen);
  }

  private Claim saveClaim(
      String claimantId,
      String market,
      String loss,
      String currency,
      ClaimStatus status,
      String officerId,
      long offset) {
    var submittedAt = NOW.plusSeconds(offset);
    var claim =
        Claim.submit(
            new ClaimSubmission(
                claimantId,
                ClaimType.MOTOR,
                market,
                NOW.minusSeconds(60),
                "Rear bumper was damaged",
                new BigDecimal(loss),
                currency),
            submittedAt);
    if (status != ClaimStatus.SUBMITTED) {
      claim.assign(officerId, submittedAt.plusSeconds(1));
      claim.apply(ClaimAction.START_REVIEW, officerId, null, submittedAt.plusSeconds(2));
    }
    if (status == ClaimStatus.MORE_INFORMATION_REQUIRED) {
      claim.apply(
          ClaimAction.REQUEST_MORE_INFORMATION,
          officerId,
          "send invoice",
          submittedAt.plusSeconds(3));
    } else if (status == ClaimStatus.APPROVED) {
      claim.apply(ClaimAction.APPROVE, officerId, "covered", submittedAt.plusSeconds(3));
    } else if (status == ClaimStatus.REJECTED) {
      claim.apply(ClaimAction.REJECT, officerId, "not covered", submittedAt.plusSeconds(3));
    }
    return claimStore.save(claim);
  }

  private record SeedData(
      Claim submitted,
      Claim underReview,
      Claim waiting,
      Claim approved,
      Claim rejected,
      Claim auOpen) {}
}
