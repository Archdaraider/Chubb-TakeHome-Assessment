package com.archdaraider.chubb.claims.claim.domain;

public enum ClaimType {
  MOTOR("motor"),
  PROPERTY("property");

  private final String value;

  ClaimType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
