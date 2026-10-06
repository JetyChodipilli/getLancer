package com.getlancer.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

public final class AuthRequests {
  private AuthRequests() {}

  public record Signup(
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Password(min = 12) String password,
      @NotNull @AssertTrue Boolean acceptedTerms,
      @Size(max = 100) String displayName) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Password(min = 1) String password,
      Boolean rememberMe) {}

  public record Mfa(@NotNull @Pattern(regexp = "[0-9]{6}") String totp) {}

  public record Reset(@NotBlank @Email @Size(max = 254) String email) {}

  public record Token(@NotBlank @Size(min = 20, max = 200) String token) {}

  public record Confirmation(
      @NotBlank @Size(min = 20, max = 200) String token,
      @Password(min = 12) String password,
      @Pattern(regexp = "|ACCEPT|REJECT|DELETE") String decision,
      @Min(1) @Max(5) Integer rating,
      @Size(min = 10, max = 2000) String reviewText,
      @Pattern(regexp = "ANONYMOUS|NAMED") String visibility) {}

  public record PasswordResetConfirmation(
      @NotBlank @Size(min = 20, max = 200) String token,
      @NotNull @Password(min = 12) String password) {}

  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
      ElementType.METHOD, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @Constraint(validatedBy = PasswordValidator.class)
  public @interface Password {
    String message() default "Use a valid password of at most 72 UTF-8 bytes.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    int min() default 12;
  }

  public static final class PasswordValidator implements ConstraintValidator<Password, String> {
    private int min;
    @Override public void initialize(Password constraint) { min = constraint.min(); }
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
      return value == null || (value.length() >= min && value.length() <= 72
          && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
  }
}
