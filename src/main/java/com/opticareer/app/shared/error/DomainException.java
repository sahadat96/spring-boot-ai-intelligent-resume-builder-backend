package com.opticareer.app.shared.error;

public class DomainException extends RuntimeException {
  @java.io.Serial private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public DomainException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }

  public static DomainException missing(String resource) {
    return new DomainException(ErrorCode.NOT_FOUND, resource + " was not found.");
  }

  public static DomainException invalid(String message) {
    return new DomainException(ErrorCode.VALIDATION_FAILED, message);
  }
}
