package com.getlancer.shared;

import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> api(ApiError e) {
    return ResponseEntity.status(e.status)
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    e.code,
                    "message",
                    e.getMessage(),
                    "requestId",
                    Support.id().toString(),
                    "fieldErrors",
                    e.fieldErrors)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(IllegalArgumentException e) {
    String code =
        Set.of("UNSAFE_EXTERNAL_URL", "INVALID_STATE_TRANSITION").contains(e.getMessage())
            ? e.getMessage()
            : "VALIDATION_ERROR";
    return api(
        new ApiError(
            code.equals("INVALID_STATE_TRANSITION") ? 409 : 400,
            code,
            code.equals("UNSAFE_EXTERNAL_URL")
                ? "Use an HTTPS URL on a public domain."
                : "This action or field is not valid."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return api(new ApiError(409, "CONFLICT", "This record already exists or cannot be changed."));
  }

  @ExceptionHandler({
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.http.converter.HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> malformed(Exception e) {
    return api(new ApiError(400, "VALIDATION_ERROR", "Check the request fields and try again."));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> oversized() {
    return api(new ApiError(413, "PAYLOAD_TOO_LARGE", "Choose a file smaller than 5 MB."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    String ref = Support.id().toString();
    org.slf4j.LoggerFactory.getLogger(Errors.class)
        .error("request_failed ref={} type={}", ref, e.getClass().getSimpleName());
    return ResponseEntity.status(500)
        .body(
            Map.of(
                "error",
                Map.of(
                    "code",
                    "INTERNAL_ERROR",
                    "message",
                    "We could not complete this request.",
                    "requestId",
                    ref)));
  }
}
