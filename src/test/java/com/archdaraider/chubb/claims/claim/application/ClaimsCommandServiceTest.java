package com.archdaraider.chubb.claims.claim.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.archdaraider.chubb.claims.claim.domain.Claim;
import com.archdaraider.chubb.claims.claim.domain.ClaimAction;
import com.archdaraider.chubb.claims.claim.domain.ClaimChange;
import com.archdaraider.chubb.claims.claim.domain.ClaimSnapshot;
import com.archdaraider.chubb.claims.claim.domain.ClaimStatus;
import com.archdaraider.chubb.claims.claim.domain.ClaimType;
import com.archdaraider.chubb.claims.claim.domain.DomainRuleException;
import com.archdaraider.chubb.claims.claim.persistence.ClaimEvidenceJpaAdapter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(ClaimsCommandServiceTest.CommandTestConfiguration.class)
class ClaimsCommandServiceTest {
  private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

  @Autowired private ClaimsCommandService service;
  @Autowired private ClaimStore claimStore;
  @Autowired private ClaimEvidenceStore evidenceStore;
  @Autowired private JdbcClient jdbc;
  @Autowired private EvidenceFailure evidenceFailure;
  @Autowired private Clock clock;

  @BeforeEach
  void clearDatabase() {
    evidenceFailure.disable();
    jdbc.sql("delete from outbox_messages").update();
    jdbc.sql("delete from claim_timeline").update();
    jdbc.sql("delete from claims").update();
  }

  @Test
  void submitWritesClaimTimelineAndOutbox() {
    var saved = service.submit(submission());

    assertThat(saved.status()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(saved.version()).isZero();
    assertThat(rowCount("claims")).isOne();
    assertThat(rowCount("claim_timeline")).isOne();
    assertThat(rowCount("outbox_messages")).isOne();
    assertThat(evidenceStore.findTimeline(saved.id()))
        .extracting(TimelineItem::eventType)
        .containsExactly("claim_submitted");
  }

  @Test
  void assignUpdatesTheClaimAndWritesMatchingEvidence() {
    var submitted = service.submit(submission());

    var assigned = service.assign(new AssignClaimCommand(submitted.id(), "officer-7"));

    assertThat(assigned.assigneeId()).isEqualTo("officer-7");
    assertThat(assigned.version()).isEqualTo(1L);
    assertThat(evidenceStore.findTimeline(assigned.id()))
        .extracting(TimelineItem::eventType)
        .containsExactly("claim_submitted", "claim_assigned");
    assertThat(rowCount("outbox_messages")).isEqualTo(2);
  }

  @Test
  void startReviewChangesStatusAndWritesTwoEvidenceRows() {
    var assigned = assignedClaim();

    var reviewed =
        service.apply(
            new ApplyClaimActionCommand(
                assigned.id(), ClaimAction.START_REVIEW, "officer-7", null));

    assertThat(reviewed.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
    assertThat(rowCount("claim_timeline")).isEqualTo(3);
    assertThat(rowCount("outbox_messages")).isEqualTo(3);
  }

  @Test
  void claimantInformationKeepsStatusAndWritesTheText() {
    var reviewed = underReviewClaim();
    var waiting =
        service.apply(
            new ApplyClaimActionCommand(
                reviewed.id(),
                ClaimAction.REQUEST_MORE_INFORMATION,
                "officer-7",
                "send repair invoice"));

    var updated =
        service.provideInformation(
            new ProvideInformationCommand(
                waiting.id(), "claimant-101", "invoice reference inv-22"));

    assertThat(updated.status()).isEqualTo(ClaimStatus.MORE_INFORMATION_REQUIRED);
    assertThat(evidenceStore.findTimeline(updated.id()).getLast())
        .extracting(TimelineItem::eventType, TimelineItem::detail)
        .containsExactly("additional_information_provided", "invoice reference inv-22");
  }

  @Test
  void approvalStoresTerminalStatusAndReason() {
    var reviewed = underReviewClaim();

    var approved =
        service.apply(
            new ApplyClaimActionCommand(
                reviewed.id(), ClaimAction.APPROVE, "officer-7", "covered"));

    assertThat(approved.status()).isEqualTo(ClaimStatus.APPROVED);
    assertThat(approved.decisionReason()).isEqualTo("covered");
    assertThat(evidenceStore.findTimeline(approved.id()).getLast().eventType())
        .isEqualTo("claim_approved");
  }

  @Test
  void missingClaimReturnsStableNotFoundCode() {
    assertThatThrownBy(() -> service.assign(new AssignClaimCommand(UUID.randomUUID(), "officer-7")))
        .isInstanceOf(ClaimNotFoundException.class)
        .extracting(error -> ((ClaimNotFoundException) error).code())
        .isEqualTo("claim_not_found");
  }

  @Test
  void rejectedDomainRuleLeavesAllStoredEvidenceUnchanged() {
    var submitted = service.submit(submission());

    assertThatThrownBy(
            () ->
                service.apply(
                    new ApplyClaimActionCommand(
                        submitted.id(), ClaimAction.APPROVE, "officer-7", "too early")))
        .isInstanceOf(DomainRuleException.class);
    assertThat(claimStore.findById(submitted.id()).orElseThrow().snapshot()).isEqualTo(submitted);
    assertThat(rowCount("claim_timeline")).isOne();
    assertThat(rowCount("outbox_messages")).isOne();
  }

  @Test
  void evidenceFailureRollsBackTheClaimUpdate() {
    var submitted = service.submit(submission());
    evidenceFailure.enable();

    assertThatThrownBy(() -> service.assign(new AssignClaimCommand(submitted.id(), "officer-7")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced_evidence_failure");

    var unchanged = claimStore.findById(submitted.id()).orElseThrow().snapshot();
    assertThat(unchanged.assigneeId()).isNull();
    assertThat(unchanged.version()).isZero();
    assertThat(rowCount("claim_timeline")).isOne();
    assertThat(rowCount("outbox_messages")).isOne();
  }

  @Test
  void staleSaveBecomesStableConflictCode() {
    var submitted = service.submit(submission());
    var loaded = claimStore.findById(submitted.id()).orElseThrow();
    var conflictingStore = new ConflictingClaimStore(loaded);
    var conflictService = new ClaimsCommandService(conflictingStore, evidenceStore, clock);

    assertThatThrownBy(
            () -> conflictService.assign(new AssignClaimCommand(submitted.id(), "officer-7")))
        .isInstanceOf(ClaimConflictException.class)
        .hasMessage("the claim changed; reload and try again")
        .extracting(error -> ((ClaimConflictException) error).code())
        .isEqualTo("claim_conflict");
  }

  private ClaimSnapshot submittedClaim() {
    return service.submit(submission());
  }

  private ClaimSnapshot assignedClaim() {
    var submitted = submittedClaim();
    return service.assign(new AssignClaimCommand(submitted.id(), "officer-7"));
  }

  private ClaimSnapshot underReviewClaim() {
    var assigned = assignedClaim();
    return service.apply(
        new ApplyClaimActionCommand(assigned.id(), ClaimAction.START_REVIEW, "officer-7", null));
  }

  private SubmitClaimCommand submission() {
    return new SubmitClaimCommand(
        "claimant-101",
        ClaimType.MOTOR,
        "SG",
        NOW.minusSeconds(60),
        "Rear bumper was damaged",
        new BigDecimal("2500.00"),
        "SGD");
  }

  private long rowCount(String table) {
    return jdbc.sql("select count(*) from " + table).query(Long.class).single();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CommandTestConfiguration {
    @Bean
    @Primary
    Clock fixedTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    EvidenceFailure evidenceFailure() {
      return new EvidenceFailure();
    }

    @Bean
    @Primary
    ClaimEvidenceStore controlledEvidenceStore(
        ClaimEvidenceJpaAdapter delegate, EvidenceFailure failure) {
      return new ClaimEvidenceStore() {
        @Override
        public void append(UUID claimId, ClaimChange change) {
          if (failure.enabled()) {
            throw new IllegalStateException("forced_evidence_failure");
          }
          delegate.append(claimId, change);
        }

        @Override
        public List<TimelineItem> findTimeline(UUID claimId) {
          return delegate.findTimeline(claimId);
        }
      };
    }
  }

  static final class EvidenceFailure {
    private final AtomicBoolean enabled = new AtomicBoolean();

    void enable() {
      enabled.set(true);
    }

    void disable() {
      enabled.set(false);
    }

    boolean enabled() {
      return enabled.get();
    }
  }

  private static final class ConflictingClaimStore implements ClaimStore {
    private final Claim loaded;

    private ConflictingClaimStore(Claim loaded) {
      this.loaded = loaded;
    }

    @Override
    public Optional<Claim> findById(UUID claimId) {
      return Optional.of(loaded);
    }

    @Override
    public Claim save(Claim claim) {
      throw new ObjectOptimisticLockingFailureException(Claim.class, claim.snapshot().id());
    }

    @Override
    public List<Claim> findForQueue(ClaimStatus status, String assigneeId) {
      return List.of();
    }

    @Override
    public List<Claim> findOpenByMarket(String market) {
      return List.of();
    }
  }
}
