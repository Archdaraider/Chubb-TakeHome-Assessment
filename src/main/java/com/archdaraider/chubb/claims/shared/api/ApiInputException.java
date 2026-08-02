package com.archdaraider.chubb.claims.shared.api;

public class ApiInputException extends RuntimeException {
  private final String code;

  public ApiInputException(String code, String message) {
    super(message);
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("api input code is required");
    }
    this.code = code;
  }

  public String code() {
    return code;
  }
}
