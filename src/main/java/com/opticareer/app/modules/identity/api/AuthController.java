package com.opticareer.app.modules.identity.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.opticareer.app.platform.config.AppProperties;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

  private final AppProperties properties;

  public AuthController(AppProperties properties) {
    this.properties = properties;
  }

  @PostMapping("/auth/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> register(@Valid @RequestBody Register r) {
    service.register(r.email(), r.password());
    return Map.of("message", "If registration can proceed, check your email.");
  }
