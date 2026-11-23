package com.opticareer.app.modules.identity.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.opticareer.app.modules.identity.application.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Endpoints for user authentication, registration, and credential management")
@RestController
@RequestMapping("/api/v1")
public class AuthController {

  private final IdentityService service;

  public AuthController(IdentityService service) {
    this.service = service;
  }

  @Schema(
      name = "RegisterRequest",
      description = "User registration payload containing full name, email address, and secure password")
  public record Register(
      @JsonProperty("fullName")
      @Schema(
          name = "fullName",
          description = "User's full name as displayed across their profile and resumes (from Figma registration form)",
          example = "Emran Hossain",
          requiredMode = Schema.RequiredMode.REQUIRED,
          minLength = 2,
          maxLength = 150)
      @NotBlank
      @Size(min = 2, max = 150)
      String fullName,

      @JsonProperty("email")
      @Schema(
          name = "email",
          description = "User email address used for account identity and verification",
          example = "candidate@example.com",
          requiredMode = Schema.RequiredMode.REQUIRED,
          maxLength = 254)
      @NotBlank
      @Email
      @Size(max = 254)
      String email,

      @JsonProperty("password")
      @Schema(
          name = "password",
          description = "Password meeting security policy (min 12, max 72 characters, >= 4 unique characters, non-breached)",
          example = "CorrectHorseBatteryStaple123!",
          requiredMode = Schema.RequiredMode.REQUIRED,
          minLength = 12,
          maxLength = 72)
      @NotBlank
      @Size(min = 12, max = 72)
      String password) {}

  @Schema(
      name = "RegisterResponse",
      description = "Acknowledgment response returned when registration is accepted")
  public record RegisterResponse(
      @JsonProperty("message")
      @Schema(
          name = "message",
          description = "Informational status message",
          example = "If registration can proceed, check your email.")
      String message) {}

  @Operation(
      summary = "Register user account",
      description = "Registers a new user with full name, email, and password. Generates a secure verification token and triggers email verification asynchronously.")
  @io.swagger.v3.oas.annotations.parameters.RequestBody(
      description = "User registration details including full name, email, and secure password",
      required = true,
      content = @Content(
          mediaType = "application/json",
          schema = @Schema(implementation = Register.class),
          examples = @ExampleObject(
              name = "DefaultRegistrationExample",
              summary = "Standard registration request example",
              value = "{\n  \"fullName\": \"Emran Hossain\",\n  \"email\": \"candidate@example.com\",\n  \"password\": \"CorrectHorseBatteryStaple123!\"\n}"
          )
      )
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "202",
          description = "Registration accepted. Verification email sent if eligible.",
          content = @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = RegisterResponse.class),
              examples = @ExampleObject(
                  name = "AcceptedResponseExample",
                  summary = "Registration accepted example",
                  value = "{\n  \"message\": \"If registration can proceed, check your email.\"\n}"
              ))),
      @ApiResponse(
          responseCode = "400",
          description = "Validation failure (e.g. missing full name, invalid email format, weak password, or breached password)",
          content = @Content(
              mediaType = "application/problem+json",
              examples = @ExampleObject(
                  name = "ValidationProblemExample",
                  summary = "Validation error example",
                  value = "{\n  \"type\": \"about:blank\",\n  \"title\": \"Bad Request\",\n  \"status\": 400,\n  \"detail\": \"Provide a full name between 2 and 150 characters.\",\n  \"code\": \"VALIDATION_FAILED\",\n  \"errors\": [{\"field\": \"fullName\", \"message\": \"must not be blank\"}]\n}"
              ))),
      @ApiResponse(
          responseCode = "503",
          description = "Password or verification provider temporarily unavailable",
          content = @Content(mediaType = "application/problem+json"))
  })
  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public RegisterResponse register(@Valid @RequestBody Register r) {
    service.register(r.fullName(), r.email(), r.password());
    return new RegisterResponse("If registration can proceed, check your email.");
  }
}
