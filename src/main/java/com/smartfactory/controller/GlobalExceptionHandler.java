package com.smartfactory.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        String rawReason = ex.getReason();
        String code = "ERROR";
        if (ex.getStatusCode() instanceof HttpStatus httpStatus) {
            code = httpStatus.name();
        } else {
            code = ex.getStatusCode().toString();
        }
        String message = rawReason;

        if (rawReason != null) {
            int colonIdx = rawReason.indexOf(": ");
            if (colonIdx > 0 && rawReason.substring(0, colonIdx).matches("^[A-Z0-9_]+$")) {
                code = rawReason.substring(0, colonIdx);
                message = rawReason.substring(colonIdx + 2);
            } else if (rawReason.matches("^[A-Z0-9_]+$")) {
                code = rawReason;
            }
        }

        log.warn("API Exception: {} - {}", code, message);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ex.getStatusCode().value());
        body.put("code", code);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, ex.getStatusCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", details);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("code", "VALIDATION_ERROR");
        body.put("message", details);
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("code", "UNAUTHORIZED");
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied error: {}", ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("code", "FORBIDDEN");
        body.put("message", "Access is denied");
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Internal server error: ", ex);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("code", "INTERNAL_SERVER_ERROR");
        body.put("message", "An unexpected internal error occurred");
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
