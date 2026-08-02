package com.archdaraider.chubb.claims.claim.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class ClaimRequests {
  private ClaimRequests() {}

  public record SubmitClaimRequest(
      @NotBlank @Size(max = 100) String claimantId,
      @NotBlank @Size(max = 30) String type,
      @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String market,
      @NotNull Instant incidentAt,
      @NotBlank @Size(min = 10, max = 2000) String description,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal estimatedLoss,
      @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency) {}

  public record AssignmentRequest(@NotBlank @Size(max = 100) String officerId) {}

  public record ActionRequest(
      @NotBlank @Size(max = 50) String action,
      @NotBlank @Size(max = 100) String officerId,
      @Size(max = 2000) String reason) {}

  public record InformationRequest(
      @NotBlank @Size(max = 100) String claimantId,
      @NotBlank @Size(max = 2000) String information) {}
}
