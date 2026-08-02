package com.archdaraider.chubb.claims.shared.api;

import com.archdaraider.chubb.claims.claim.application.ClaimConflictException;
import com.archdaraider.chubb.claims.claim.application.ClaimNotFoundException;
import com.archdaraider.chubb.claims.claim.domain.DomainRuleException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private static final Map<String, String> VALIDATION_CODES =
      Map.of(
          "action", "action_invalid",
          "claimantId", "claimant_required",
          "currency", "currency_invalid",
          "description", "description_invalid",
          "estimatedLoss", "estimated_loss_invalid",
          "incidentAt", "incident_time_invalid",
          "information", "information_required",
          "market", "market_invalid",
          "officerId", "officer_required",
          "type", "claim_type_required");

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    var firstError =
        exception.getBindingResult().getFieldErrors().stream()
            .sorted(Comparator.comparing(error -> error.getField()))
            .findFirst();
    var code =
        firstError
            .map(error -> VALIDATION_CODES.getOrDefault(error.getField(), "request_invalid"))
            .orElse("request_invalid");
    return problem(
        HttpStatus.BAD_REQUEST, "request invalid", "request validation failed", code, request);
  }

  @ExceptionHandler(ApiInputException.class)
  ResponseEntity<ProblemDetail> input(ApiInputException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "request invalid",
        exception.getMessage(),
        exception.code(),
        request);
  }

  @ExceptionHandler(ClaimNotFoundException.class)
  ResponseEntity<ProblemDetail> notFound(
      ClaimNotFoundException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.NOT_FOUND, "claim not found", exception.getMessage(), exception.code(), request);
  }

  @ExceptionHandler(DomainRuleException.class)
  ResponseEntity<ProblemDetail> domain(DomainRuleException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "claim change rejected",
        safeDomainDetail(exception.code()),
        exception.code(),
        request);
  }

  @ExceptionHandler(ClaimConflictException.class)
  ResponseEntity<ProblemDetail> conflict(
      ClaimConflictException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT, "claim changed", exception.getMessage(), exception.code(), request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ProblemDetail> malformed(Exception exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "request invalid",
        "the request could not be read",
        "request_invalid",
        request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
    LOGGER.error("unexpected api error", exception);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "server error",
        "the request could not be completed",
        "server_error",
        request);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String title, String detail, String code, HttpServletRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("code", code);
    return ResponseEntity.status(status).body(problem);
  }

  private String safeDomainDetail(String code) {
    return switch (code) {
      case "claim_officer_mismatch" -> "the officer does not match the assigned officer";
      case "claim_transition_invalid" -> "the requested claim action is not allowed";
      case "claim_closed" -> "the claim is already closed";
      default -> "the claim rule was not satisfied";
    };
  }
}
