package com.getlancer.shared;



import java.util.Map;



public class ApiError extends RuntimeException {
  public final int status;
  public final String code;
  public final Map<String, String> fieldErrors;

  public ApiError(int status, String code, String message) {
    this(status, code, message, Map.of());
  }

  public ApiError(int status, String code, String message, Map<String, String> fields) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fields;
  }
}
