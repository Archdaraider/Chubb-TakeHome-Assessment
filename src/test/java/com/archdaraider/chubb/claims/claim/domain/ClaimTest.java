package com.archdaraider.chubb.claims.claim.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ClaimTest {
  private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");

  @Test
  void validSubmissionCreatesSubmittedClaim() {
    var claim = Claim.submit(validSubmission(), NOW);

    assertThat(claim.snapshot().id()).isNotNull();
    assertThat(claim.snapshot().claimantId()).isEqualTo("claimant-101");
    assertThat(claim.snapshot().claimType()).isEqualTo(ClaimType.MOTOR);
    assertThat(claim.snapshot().market()).isEqualTo("SG");
    assertThat(claim.snapshot().description()).isEqualTo("Rear bumper was damaged");
    assertThat(claim.snapshot().estimatedLoss()).isEqualByComparingTo("2500.00");
    assertThat(claim.snapshot().currency()).isEqualTo("SGD");
    assertThat(claim.snapshot().status()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(claim.snapshot().submittedAt()).isEqualTo(NOW);
    assertThat(claim.snapshot().updatedAt()).isEqualTo(NOW);
    assertThat(claim.snapshot().version()).isNull();
  }

  @ParameterizedTest
  @MethodSource("invalidSubmissions")
  void invalidSubmissionReturnsStableCode(ClaimSubmission input, String code) {
    assertCode(code, () -> Claim.submit(input, NOW));
  }

  @Test
  void unassignedSubmittedClaimCanBeAssignedOnce() {
    var claim = Claim.submit(validSubmission(), NOW);

    var change = claim.assign(" officer-7 ", NOW.plusSeconds(1));

    assertThat(change.eventType()).isEqualTo("claim_assigned");
    assertThat(change.resultingStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    assertThat(change.actorId()).isEqualTo("officer-7");
    assertThat(claim.snapshot().assigneeId()).isEqualTo("officer-7");
    assertCode("claim_already_assigned", () -> claim.assign("officer-8", NOW.plusSeconds(2)));
  }

  @Test
  void reviewRequiresAssignedOfficer() {
    var claim = Claim.submit(validSubmission(), NOW);

    assertCode(
        "claim_officer_mismatch",
        () -> claim.apply(ClaimAction.START_REVIEW, "officer-7", null, NOW));
    assertCode("officer_required", () -> claim.assign(" ", NOW));
  }

  @Test
  void assignedOfficerCanCompleteTheFullReviewPath() {
    var claim = assignedClaim();

    assertThat(claim.apply(ClaimAction.START_REVIEW, "officer-7", null, next(2)))
        .extracting(ClaimChange::eventType, ClaimChange::resultingStatus)
        .containsExactly("review_started", ClaimStatus.UNDER_REVIEW);
    assertThat(
            claim.apply(
                ClaimAction.REQUEST_MORE_INFORMATION, "officer-7", "send repair invoice", next(3)))
        .extracting(ClaimChange::eventType, ClaimChange::resultingStatus)
        .containsExactly("more_information_requested", ClaimStatus.MORE_INFORMATION_REQUIRED);
    assertThat(claim.provideInformation("claimant-101", "invoice reference inv-22", next(4)))
        .extracting(ClaimChange::eventType, ClaimChange::resultingStatus)
        .containsExactly("additional_information_provided", ClaimStatus.MORE_INFORMATION_REQUIRED);
    assertThat(claim.apply(ClaimAction.RESUME_REVIEW, "officer-7", null, next(5)))
        .extracting(ClaimChange::eventType, ClaimChange::resultingStatus)
        .containsExactly("review_resumed", ClaimStatus.UNDER_REVIEW);
    assertThat(claim.apply(ClaimAction.APPROVE, "officer-7", "covered", next(6)))
        .extracting(ClaimChange::eventType, ClaimChange::resultingStatus)
        .containsExactly("claim_approved", ClaimStatus.APPROVED);
    assertThat(claim.snapshot().decisionReason()).isEqualTo("covered");
  }

  @Test
  void requestForInformationRequiresReason() {
    var claim = underReviewClaim();

    assertCode(
        "reason_required",
        () -> claim.apply(ClaimAction.REQUEST_MORE_INFORMATION, "officer-7", " ", next(3)));
  }

  @Test
  void claimantCanProvideInformationOnlyWhenRequested() {
    var claim = underReviewClaim();

    assertCode(
        "claim_transition_invalid",
        () -> claim.provideInformation("claimant-101", "invoice", next(3)));
    claim.apply(ClaimAction.REQUEST_MORE_INFORMATION, "officer-7", "send invoice", next(4));
    assertCode(
        "information_required", () -> claim.provideInformation("claimant-101", " ", next(5)));
  }

  @Test
  void differentClaimantCannotProvideInformation() {
    var claim = informationRequestedClaim();

    assertCode(
        "claimant_mismatch", () -> claim.provideInformation("claimant-999", "invoice", next(4)));
  }

  @Test
  void differentOfficerCannotChangeTheClaim() {
    var claim = assignedClaim();

    assertCode(
        "claim_officer_mismatch",
        () -> claim.apply(ClaimAction.START_REVIEW, "officer-8", null, next(2)));
  }

  @Test
  void approvalAndRejectionRequireReason() {
    var approval = underReviewClaim();
    var rejection = underReviewClaim();

    assertCode(
        "reason_required", () -> approval.apply(ClaimAction.APPROVE, "officer-7", null, next(3)));
    assertCode(
        "reason_required", () -> rejection.apply(ClaimAction.REJECT, "officer-7", " ", next(3)));
  }

  @Test
  void invalidTransitionsReturnClaimTransitionInvalid() {
    var claim = assignedClaim();

    assertCode(
        "claim_transition_invalid",
        () -> claim.apply(ClaimAction.APPROVE, "officer-7", "too early", next(2)));
  }

  @Test
  void terminalClaimRejectsFurtherChanges() {
    var claim = underReviewClaim();
    claim.apply(ClaimAction.APPROVE, "officer-7", "covered", next(3));

    assertCode(
        "claim_closed", () -> claim.apply(ClaimAction.REJECT, "officer-7", "changed", next(4)));
    assertCode(
        "claim_closed", () -> claim.provideInformation("claimant-101", "late info", next(4)));
  }

  private static Stream<Arguments> invalidSubmissions() {
    return Stream.of(
        Arguments.of(
            submission(" ", ClaimType.MOTOR, "SG", NOW, "valid details", "1", "SGD"),
            "claimant_required"),
        Arguments.of(
            submission("c", null, "SG", NOW, "valid details", "1", "SGD"), "claim_type_required"),
        Arguments.of(
            submission("c", ClaimType.MOTOR, "SGP", NOW, "valid details", "1", "SGD"),
            "market_invalid"),
        Arguments.of(
            submission("c", ClaimType.MOTOR, "SG", NOW.plusSeconds(1), "valid details", "1", "SGD"),
            "incident_time_invalid"),
        Arguments.of(
            submission("c", ClaimType.MOTOR, "SG", NOW, "123456789", "1", "SGD"),
            "description_invalid"),
        Arguments.of(
            submission("c", ClaimType.MOTOR, "SG", NOW, "valid details", "0", "SGD"),
            "estimated_loss_invalid"),
        Arguments.of(
            submission("c", ClaimType.MOTOR, "SG", NOW, "valid details", "1", "SG"),
            "currency_invalid"));
  }

  private static ClaimSubmission validSubmission() {
    return submission(
        " claimant-101 ",
        ClaimType.MOTOR,
        "sg",
        NOW.minusSeconds(60),
        " Rear bumper was damaged ",
        "2500.00",
        "sgd");
  }

  private static ClaimSubmission submission(
      String claimantId,
      ClaimType claimType,
      String market,
      Instant incidentAt,
      String description,
      String estimatedLoss,
      String currency) {
    return new ClaimSubmission(
        claimantId,
        claimType,
        market,
        incidentAt,
        description,
        new BigDecimal(estimatedLoss),
        currency);
  }

  private static Claim assignedClaim() {
    var claim = Claim.submit(validSubmission(), NOW);
    claim.assign("officer-7", next(1));
    return claim;
  }

  private static Claim underReviewClaim() {
    var claim = assignedClaim();
    claim.apply(ClaimAction.START_REVIEW, "officer-7", null, next(2));
    return claim;
  }

  private static Claim informationRequestedClaim() {
    var claim = underReviewClaim();
    claim.apply(ClaimAction.REQUEST_MORE_INFORMATION, "officer-7", "send invoice", next(3));
    return claim;
  }

  private static Instant next(long seconds) {
    return NOW.plusSeconds(seconds);
  }

  private static void assertCode(String expected, Runnable operation) {
    assertThatThrownBy(operation::run)
        .isInstanceOf(DomainRuleException.class)
        .extracting(error -> ((DomainRuleException) error).code())
        .isEqualTo(expected);
  }
}
