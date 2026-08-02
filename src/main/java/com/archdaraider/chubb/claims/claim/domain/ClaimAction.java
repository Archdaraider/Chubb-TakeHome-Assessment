package com.archdaraider.chubb.claims.claim.domain;

public enum ClaimAction {
  START_REVIEW("startReview"),
  REQUEST_MORE_INFORMATION("requestMoreInformation"),
  RESUME_REVIEW("resumeReview"),
  APPROVE("approve"),
  REJECT("reject");

  private final String value;

  ClaimAction(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
