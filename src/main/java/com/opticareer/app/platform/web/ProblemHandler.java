package com.opticareer.app.platform.web;

import com.opticareer.app.shared.error.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ProblemHandler {
  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> domain(DomainException e, HttpServletRequest request) {
    int status =
        switch (e.code()) {
          case NOT_FOUND -> 404;
          case UNAUTHENTICATED, INVALID_TOKEN, SESSION_REUSE -> 401;
          case FORBIDDEN, EMAIL_NOT_VERIFIED, DELETION_PENDING -> 403;
          case CONFLICT, CONCURRENT_EDIT, PLAN_LIMIT_REACHED, IDEMPOTENCY_CONFLICT -> 409;
          case RATE_LIMITED -> 429;
          case AI_UNAVAILABLE, PROVIDER_UNAVAILABLE -> 503;
          case INTERNAL_ERROR -> 500;
          default -> 400;
        };
    return problem(status, e.code(), e.getMessage(), request, List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    var errors =
        e.getBindingResult().getFieldErrors().stream()
            .map(
                x ->
                    Map.of(
                        "field",
                        x.getField(),
                        "message",
                        Objects.toString(x.getDefaultMessage(), "Invalid value")))
            .toList();
    return problem(
        400, ErrorCode.VALIDATION_FAILED, "Correct the invalid fields.", request, errors);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest request) {
    return problem(
        400,
        ErrorCode.VALIDATION_FAILED,
        "The request has an invalid value or format.",
        request,
        List.of());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ProblemDetail> upload(Exception e, HttpServletRequest request) {
    return problem(
        413, ErrorCode.FILE_REJECTED, "Files must be at most 10 MB.", request, List.of());
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    org.springframework.orm.ObjectOptimisticLockingFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e, HttpServletRequest request) {
    return problem(
        409, ErrorCode.CONFLICT, "The operation conflicts with current data.", request, List.of());
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<ProblemDetail> denied(Exception e, HttpServletRequest request) {
    return problem(403, ErrorCode.FORBIDDEN, "Access denied.", request, List.of());
  }

  @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
  ResponseEntity<ProblemDetail> binding(Exception e, HttpServletRequest request) {
    return problem(
        400,
        ErrorCode.VALIDATION_FAILED,
        "A required request header or parameter is missing.",
        request,
        List.of());
  }

  @ExceptionHandler(org.springframework.web.ErrorResponseException.class)
  ResponseEntity<ProblemDetail> http(
      org.springframework.web.ErrorResponseException e, HttpServletRequest r) {
    return problem(
        e.getStatusCode().value(),
        ErrorCode.VALIDATION_FAILED,
        e.getStatusCode().value() == 404
            ? "Resource not found."
            : "The HTTP request is not supported.",
        r,
        List.of());
  }

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  ResponseEntity<ProblemDetail> notFound(Exception e, HttpServletRequest r) {
    return problem(404, ErrorCode.NOT_FOUND, "Resource not found.", r, List.of());
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ProblemDetail> method(
      org.springframework.web.HttpRequestMethodNotSupportedException e, HttpServletRequest r) {
    return problem(405, ErrorCode.VALIDATION_FAILED, "HTTP method is not supported.", r, List.of());
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ProblemDetail> media(Exception e, HttpServletRequest r) {
    return problem(
        415, ErrorCode.VALIDATION_FAILED, "Content type is not supported.", r, List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> internal(Exception e, HttpServletRequest request) {
    org.slf4j.LoggerFactory.getLogger(getClass())
        .error(
            "Unhandled {} traceId={}",
            e.getClass().getSimpleName(),
            request.getAttribute("traceId"),
            e);
    return problem(
        500, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", request, List.of());
  }

  private ResponseEntity<ProblemDetail> problem(
      int status, ErrorCode code, String detail, HttpServletRequest request, List<?> errors) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
    p.setTitle(code.name().replace('_', ' '));
    p.setType(URI.create("urn:opticareer:problem:" + code.name().toLowerCase(Locale.ROOT)));
    p.setProperty("code", code.name());
    p.setProperty("traceId", request.getAttribute("traceId"));
    p.setProperty("errors", errors);
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(p);
  }
}
