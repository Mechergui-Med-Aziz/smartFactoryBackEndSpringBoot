package com.smartfactory.service;

import com.smartfactory.dto.request.*;
import com.smartfactory.dto.response.LoginResponse;
import com.smartfactory.dto.response.MessageResponse;
import com.smartfactory.dto.response.UserResponse;
import com.smartfactory.dto.response.VerifyResetOtpResponse;
import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.User;
import com.smartfactory.exception.ApiException;
import com.smartfactory.exception.ErrorCode;
import com.smartfactory.mapper.UserMapper;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.JwtService;
import com.smartfactory.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final OtpService otpService;
    private final EmailService emailService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       UserMapper userMapper,
                       OtpService otpService,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.otpService = otpService;
        this.emailService = emailService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_INACTIVE, "User account is inactive");
        }

        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.EMAIL_NOT_VERIFIED, "Adresse e-mail non vérifiée.");
        }

        String token = jwtService.generateToken(user);
        UserResponse userResponse = userMapper.toResponse(user);

        return new LoginResponse(token, userResponse);
    }

    public MessageResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.USER_ALREADY_EXISTS, "Email is already registered: " + normalizedEmail);
        }

        Role role = request.getRole() != null ? request.getRole() : Role.OPERATOR;

        User user = new User(
                request.getFirstName().trim(),
                request.getLastName().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.getPassword()),
                role,
                "ACTIVE"
        );
        user.setEmailVerified(false);
        userRepository.save(user);

        // Generate OTP and send email
        String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.REGISTER);
        emailService.sendVerificationEmail(normalizedEmail, otp);

        log.info("Registered new user with unverified email: {}", normalizedEmail);
        return new MessageResponse("Compte créé. Un code de vérification a été envoyé à votre adresse e-mail.");
    }

    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Adresse e-mail déjà vérifiée.");
        }

        otpService.verifyEmailOtp(normalizedEmail, request.getOtp().trim());

        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for user: {}", normalizedEmail);
        return new MessageResponse("Adresse e-mail vérifiée avec succès.");
    }

    public MessageResponse resendVerification(ResendVerificationRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with email: " + normalizedEmail));

        if (user.isEmailVerified()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.BAD_REQUEST, "Adresse e-mail déjà vérifiée.");
        }

        String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.REGISTER);
        emailService.sendVerificationEmail(normalizedEmail, otp);

        log.info("Resent verification email for user: {}", normalizedEmail);
        return new MessageResponse("Un nouveau code de vérification a été envoyé à votre adresse e-mail.");
    }

    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isPresent()) {
            String otp = otpService.generateAndSaveOtp(normalizedEmail, OtpType.RESET_PASSWORD);
            emailService.sendPasswordResetEmail(normalizedEmail, otp);
            log.info("Password reset requested for existing user: {}", normalizedEmail);
        } else {
            log.info("Password reset requested for non-existing user: {}", normalizedEmail);
        }

        // Always return generic response to prevent account enumeration
        return new MessageResponse("Si un compte correspond à cette adresse e-mail, un code de réinitialisation a été envoyé.");
    }

    public VerifyResetOtpResponse verifyResetOtp(VerifyResetOtpRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        if (!userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.OTP_INVALID, "Code OTP invalide.");
        }

        String resetToken = otpService.verifyResetOtp(normalizedEmail, request.getOtp().trim());
        log.info("Reset OTP verified for: {}", normalizedEmail);
        return new VerifyResetOtpResponse("Code OTP vérifié avec succès.", resetToken);
    }

    public MessageResponse resetPassword(ResetPasswordRequest request) {
        String normalizedEmail = request.getEmail().toLowerCase().trim();

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found with email: " + normalizedEmail));

        otpService.validateAndConsumeResetToken(normalizedEmail, request.getResetToken());

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset successful for user: {}", normalizedEmail);
        return new MessageResponse("Mot de passe réinitialisé avec succès.");
    }

    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND, "User not found"));
        return userMapper.toResponse(user);
    }
}
