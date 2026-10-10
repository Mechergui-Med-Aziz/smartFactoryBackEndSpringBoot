package com.smartfactory.service;

import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.User;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.JwtService;
import com.smartfactory.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       OtpService otpService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    public Map<String, Object> login(String email, String password) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS: Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS: Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "USER_INACTIVE: User account is inactive");
        }

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED: Adresse e-mail non vérifiée.");
        }

        String token = jwtService.generateToken(user);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token", token);
        response.put("user", user);
        return response;
    }

    public String register(User userRequest) {
        String normalizedEmail = userRequest.getEmail() != null ? userRequest.getEmail().toLowerCase().trim() : "";

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS: Email is already registered: " + normalizedEmail);
        }

        Role role = userRequest.getRole() != null ? userRequest.getRole() : Role.OPERATOR;

        User user = new User(
                userRequest.getFirstName().trim(),
                userRequest.getLastName().trim(),
                normalizedEmail,
                passwordEncoder.encode(userRequest.getPassword()),
                role,
                "ACTIVE"
        );
        user.setEmailVerified(false);
        userRepository.save(user);

        // Generate OTP and send email
        String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.REGISTER);
        emailService.sendVerificationEmail(normalizedEmail, otp);

        log.info("Registered new user with unverified email: {}", normalizedEmail);
        return "Compte créé. Un code de vérification a été envoyé à votre adresse e-mail.";
    }

    public String verifyEmail(String email, String otp) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST: Adresse e-mail déjà vérifiée.");
        }

        otpService.verifyEmailOtp(normalizedEmail, otp.trim());

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for user: {}", normalizedEmail);
        return "Adresse e-mail vérifiée avec succès.";
    }

    public String resendVerification(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BAD_REQUEST: Adresse e-mail déjà vérifiée.");
        }

        String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.REGISTER);
        emailService.sendVerificationEmail(normalizedEmail, otp);

        log.info("Resent verification email for user: {}", normalizedEmail);
        return "Un nouveau code de vérification a été envoyé à votre adresse e-mail.";
    }

    public String forgotPassword(String email) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isPresent()) {
            String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.RESET_PASSWORD);
            emailService.sendPasswordResetEmail(normalizedEmail, otp);
            log.info("Password reset requested for existing user: {}", normalizedEmail);
        } else {
            log.info("Password reset requested for non-existing user: {}", normalizedEmail);
        }

        // Always return generic response to prevent account enumeration
        return "Si un compte correspond à cette adresse e-mail, un code de réinitialisation a été envoyé.";
    }

    public Map<String, String> verifyResetOtp(String email, String otp) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";

        if (!userRepository.existsByEmail(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP_INVALID: Code OTP invalide.");
        }

        String resetToken = otpService.verifyResetOtp(normalizedEmail, otp.trim());
        log.info("Reset OTP verified for: {}", normalizedEmail);

        Map<String, String> response = new LinkedHashMap<>();
        response.put("message", "Code OTP vérifié avec succès.");
        response.put("resetToken", resetToken);
        return response;
    }

    public String resetPassword(String email, String resetToken, String newPassword) {
        String normalizedEmail = email != null ? email.toLowerCase().trim() : "";

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found with email: " + normalizedEmail));

        otpService.validateAndConsumeResetToken(normalizedEmail, resetToken);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.info("Password reset successful for user: {}", normalizedEmail);
        return "Mot de passe réinitialisé avec succès.";
    }

    public User getCurrentUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND: User not found"));
    }
}
