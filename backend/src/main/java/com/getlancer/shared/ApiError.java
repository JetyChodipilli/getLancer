package com.getlancer.shared;

import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

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
