package com.opticareer.app.modules.identity.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.opticareer.app.modules.identity.application.IdentityService;
import com.opticareer.app.platform.web.ProblemHandler;
import com.opticareer.app.shared.error.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

  private IdentityService service;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    service = mock(IdentityService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AuthController(service))
            .setControllerAdvice(new ProblemHandler())
            .build();
  }

  @Test
  @DisplayName("POST /api/v1/auth/register returns 202 Accepted with confirmation message")
  void registerSuccess() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.message").value("If registration can proceed, check your email."));

    verify(service).register("Emran Hossain", "applicant@example.com", "CorrectHorseBatteryStaple123!");
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with blank full name returns 400 Bad Request")
  void registerBlankFullName() throws Exception {
    String payload = """
        {
          "fullName": "   ",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("fullName"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with single char full name returns 400 Bad Request")
  void registerShortFullName() throws Exception {
    String payload = """
        {
          "fullName": "A",
          "email": "applicant@example.com",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("fullName"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with invalid email returns 400 Bad Request with ProblemDetail")
  void registerInvalidEmail() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "not-an-email",
          "password": "CorrectHorseBatteryStaple123!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("email"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register with short password returns 400 Bad Request")
  void registerShortPassword() throws Exception {
    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "Short1!"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("password"));

    verifyNoInteractions(service);
  }

  @Test
  @DisplayName("POST /api/v1/auth/register propagates domain exception through ProblemHandler")
  void registerDomainException() throws Exception {
    doThrow(DomainException.invalid("Choose a less predictable password."))
        .when(service)
        .register("Emran Hossain", "applicant@example.com", "password12341234");

    String payload = """
        {
          "fullName": "Emran Hossain",
          "email": "applicant@example.com",
          "password": "password12341234"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.detail").value("Choose a less predictable password."));
  }
}
