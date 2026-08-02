package com.archdaraider.chubb.claims.claim.domain;

public class DomainRuleException extends RuntimeException {
  private final String code;

  public DomainRuleException(String code) {
    super(code);
    this.code = code;
  }

  public String code() {
    return code;
  }
}
