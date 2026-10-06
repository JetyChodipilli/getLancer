package com.getlancer.shared;

import com.getlancer.security.BoundedRequest;
import com.getlancer.security.RequestIds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RestControllerAdvice
public class Errors {
  public static final String CODE_ATTRIBUTE = Errors.class.getName() + ".code";
  @ExceptionHandler(ApiError.class)
  public ResponseEntity<?> api(ApiError error) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
      attributes.getRequest().setAttribute(CODE_ATTRIBUTE, error.code);
    return ResponseEntity.status(error.status).body(Map.of("error", Map.of(
        "code", error.code, "message", error.getMessage(), "requestId", RequestIds.current(),
        "fieldErrors", error.fieldErrors)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException error) {
    Map<String, String> fields = new LinkedHashMap<>();
    error.getBindingResult().getFieldErrors().stream().limit(30).forEach(field ->
        fields.putIfAbsent(field.getField(), "Check this field and try again."));
    return api(new ApiError(400, "VALIDATION_ERROR", "Check the request fields and try again.", fields));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> invalid(IllegalArgumentException error) {
    String code = Set.of("UNSAFE_EXTERNAL_URL", "INVALID_STATE_TRANSITION").contains(error.getMessage())
        ? error.getMessage() : "VALIDATION_ERROR";
    return api(new ApiError(code.equals("INVALID_STATE_TRANSITION") ? 409 : 400, code,
        code.equals("UNSAFE_EXTERNAL_URL") ? "Use an HTTPS URL on a public domain." : "This action or field is not valid."));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict() {
    return api(new ApiError(409, "CONFLICT", "This record already exists or cannot be changed."));
  }

  @ExceptionHandler({MissingServletRequestPartException.class, HttpMessageNotReadableException.class,
      MissingRequestHeaderException.class, MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class})
  ResponseEntity<?> malformed(Exception error) {
    Throwable cause = error;
    for (int depth = 0; cause != null && depth < 20; depth++, cause = cause.getCause())
      if (cause instanceof BoundedRequest.TooLarge) return oversized();
    return api(new ApiError(400, "VALIDATION_ERROR", "Check the request fields and try again."));
  }

  @ExceptionHandler({MaxUploadSizeExceededException.class, BoundedRequest.TooLarge.class})
  ResponseEntity<?> oversized() {
    return api(new ApiError(413, "PAYLOAD_TOO_LARGE", "Request too large."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> forbidden() {
    return api(new ApiError(403, "FORBIDDEN", "You cannot access this resource."));
  }

  @ExceptionHandler(AuthenticationException.class)
  ResponseEntity<?> unauthenticated() {
    return api(new ApiError(401, "UNAUTHENTICATED", "Please log in to continue."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception error) {
    String ref = RequestIds.current();
    LoggerFactory.getLogger(Errors.class).error("request_failed ref={} type={}", ref, error.getClass().getSimpleName());
    return ResponseEntity.status(500).body(Map.of("error", Map.of("code", "INTERNAL_ERROR",
        "message", "We could not complete this request.", "requestId", ref)));
  }
}
