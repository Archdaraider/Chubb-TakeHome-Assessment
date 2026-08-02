package com.archdaraider.chubb.claims.claim.application;

public class ClaimNotFoundException extends RuntimeException {
  private final String code;

  public ClaimNotFoundException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
