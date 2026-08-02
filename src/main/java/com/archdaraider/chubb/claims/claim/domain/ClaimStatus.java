package com.archdaraider.chubb.claims.claim.domain;

public enum ClaimStatus {
  SUBMITTED("submitted"),
  UNDER_REVIEW("underReview"),
  MORE_INFORMATION_REQUIRED("moreInformationRequired"),
  APPROVED("approved"),
  REJECTED("rejected");

  private final String value;

  ClaimStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean isClosed() {
    return this == APPROVED || this == REJECTED;
  }
}
