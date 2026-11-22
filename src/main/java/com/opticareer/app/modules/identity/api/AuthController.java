package com.opticareer.app.modules.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opticareer.app.modules.identity.application.IdentityService;
//import com.opticareer.app.platform.config.AppProperties;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

  private final IdentityService service;

  public AuthController(IdentityService service) {
    this.service = service;
  }

  record Register(
    @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 72) String password) {}

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> register(@Valid @RequestBody Register r) {
    service.register(r.email(), r.password());
    return Map.of("message", "If registration can proceed, check your email.");
  }
}