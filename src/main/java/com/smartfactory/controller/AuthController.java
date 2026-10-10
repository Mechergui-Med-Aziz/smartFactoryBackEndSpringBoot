package com.smartfactory.controller;

import com.smartfactory.entity.User;
import com.smartfactory.security.UserPrincipal;
import com.smartfactory.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;
        String password = request != null ? request.get("password") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }
        if (!StringUtils.hasText(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password is required");
        }

        Map<String, Object> response = authService.login(email, password);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok(Map.of("message", "Successfully logged out"));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody User request) {
        if (!StringUtils.hasText(request.getFirstName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: First name is required");
        }
        if (!StringUtils.hasText(request.getLastName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Last name is required");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password is required");
        }
        if (request.getPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password must be at least 6 characters long");
        }

        String message = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("message", message));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;
        String otp = request != null ? request.get("otp") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }
        if (!StringUtils.hasText(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: OTP is required");
        }
        if (!otp.trim().matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: OTP must be a 6-digit number");
        }

        String message = authService.verifyEmail(email, otp);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }

        String message = authService.resendVerification(email);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }

        String message = authService.forgotPassword(email);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyResetOtp(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;
        String otp = request != null ? request.get("otp") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }
        if (!StringUtils.hasText(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: OTP is required");
        }
        if (!otp.trim().matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: OTP must be a 6-digit number");
        }

        Map<String, String> response = authService.verifyResetOtp(email, otp);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> request) {
        String email = request != null ? request.get("email") : null;
        String resetToken = request != null ? request.get("resetToken") : null;
        String newPassword = request != null ? request.get("newPassword") : null;

        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Email is required");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Invalid email format");
        }
        if (!StringUtils.hasText(resetToken)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Reset token is required");
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: New password is required");
        }
        if (newPassword.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR: Password must be at least 6 characters long");
        }

        String message = authService.resetPassword(email, resetToken, newPassword);
        return ResponseEntity.ok(Map.of("message", message));
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User response = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(response);
    }
}
