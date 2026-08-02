package com.archdaraider.chubb.claims.claim.application;

public class ClaimConflictException extends RuntimeException {
  private final String code;

  public ClaimConflictException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
