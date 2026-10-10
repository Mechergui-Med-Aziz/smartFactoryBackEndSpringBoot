package com.smartfactory.service;

import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.OtpVerification;
import com.smartfactory.repository.OtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    private final int expirationMinutes;
    private final int maxAttempts;
    private final int resendDelaySeconds;
    private final int resetTokenExpirationMinutes;

    public OtpService(OtpRepository otpRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${otp.expiration-minutes:10}") int expirationMinutes,
                      @Value("${otp.max-attempts:5}") int maxAttempts,
                      @Value("${otp.resend-delay-seconds:60}") int resendDelaySeconds,
                      @Value("${otp.reset-token-expiration-minutes:15}") int resetTokenExpirationMinutes) {
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.expirationMinutes = expirationMinutes;
        this.maxAttempts = maxAttempts;
        this.resendDelaySeconds = resendDelaySeconds;
        this.resetTokenExpirationMinutes = resetTokenExpirationMinutes;
    }

    /**
     * Generates a 6-digit OTP, stores its hash, and returns the raw OTP to be emailed.
     * Enforces anti-spam delay and invalidates prior unused OTPs for this email and type.
     */
    public String generateAndSaveOtp(String email, OtpType type) {
        String normalizedEmail = email.toLowerCase().trim();

        // Anti-spam check: verify if a code was created recently
        Optional<OtpVerification> latestOtpOpt = otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(normalizedEmail, type);
        if (latestOtpOpt.isPresent()) {
            OtpVerification latest = latestOtpOpt.get();
            if (latest.getCreatedAt() != null) {
                Instant canResendAt = latest.getCreatedAt().plusSeconds(resendDelaySeconds);
                if (Instant.now().isBefore(canResendAt)) {
                    log.warn("Rate limit: OTP requested too soon for {}", normalizedEmail);
                    throw new ResponseStatusException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "OTP_RESEND_TOO_SOON: Veuillez patienter " + resendDelaySeconds + " secondes avant de demander un nouveau code."
                    );
                }
            }
        }

        // Invalidate previous OTPs of the same type
        List<OtpVerification> existingOtps = otpRepository.findByEmailAndType(normalizedEmail, type);
        for (OtpVerification otp : existingOtps) {
            if (!otp.isUsed()) {
                otp.setUsed(true);
                otpRepository.save(otp);
            }
        }

        // Generate 6-digit OTP
        int code = secureRandom.nextInt(1_000_000);
        String rawOtp = String.format("%06d", code);

        // Store hash
        String otpHash = passwordEncoder.encode(rawOtp);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(expirationMinutes));

        OtpVerification verification = new OtpVerification(normalizedEmail, otpHash, type, expiresAt);
        otpRepository.save(verification);

        log.info("Generated new OTP verification for {} with type {}", normalizedEmail, type);
        return rawOtp;
    }

    /**
     * Verifies an OTP for email registration.
     */
    public void verifyEmailOtp(String email, String rawOtp) {
        verifyOtpInternal(email, rawOtp, OtpType.REGISTER);
    }

    /**
     * Verifies an OTP for password reset and returns a temporary secure reset token.
     */
    public String verifyResetOtp(String email, String rawOtp) {
        OtpVerification verification = verifyOtpInternal(email, rawOtp, OtpType.RESET_PASSWORD);

        // Generate secure reset token
        String resetToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        verification.setResetToken(resetToken);
        verification.setResetTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenExpirationMinutes)));
        verification.setResetTokenUsed(false);
        otpRepository.save(verification);

        return resetToken;
    }

    /**
     * Validates and consumes the password reset token.
     */
    public void validateAndConsumeResetToken(String email, String resetToken) {
        String normalizedEmail = email.toLowerCase().trim();

        OtpVerification verification = otpRepository.findByEmailAndResetToken(normalizedEmail, resetToken)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "RESET_TOKEN_INVALID: Token de réinitialisation invalide."
                ));

        if (verification.isResetTokenUsed()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "RESET_TOKEN_INVALID: Token de réinitialisation déjà utilisé."
            );
        }

        if (verification.getResetTokenExpiresAt() == null || Instant.now().isAfter(verification.getResetTokenExpiresAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "RESET_TOKEN_EXPIRED: Le token de réinitialisation a expiré."
            );
        }

        verification.setResetTokenUsed(true);
        otpRepository.save(verification);
        log.info("Reset token consumed successfully for {}", normalizedEmail);
    }

    private OtpVerification verifyOtpInternal(String email, String rawOtp, OtpType type) {
        String normalizedEmail = email.toLowerCase().trim();

        OtpVerification verification = otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(normalizedEmail, type)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "OTP_INVALID: Code OTP invalide."
                ));

        if (verification.isUsed()) {
            log.warn("OTP verification failed: already used for {}", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP_ALREADY_USED: Ce code OTP a déjà été utilisé."
            );
        }

        if (verification.getAttempts() >= maxAttempts) {
            log.warn("OTP verification failed: max attempts exceeded for {}", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP_MAX_ATTEMPTS: Nombre maximal de tentatives dépassé. Veuillez demander un nouveau code."
            );
        }

        if (Instant.now().isAfter(verification.getExpiresAt())) {
            log.warn("OTP verification failed: expired for {}", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP_EXPIRED: Le code OTP a expiré."
            );
        }

        if (!passwordEncoder.matches(rawOtp, verification.getOtpHash())) {
            verification.setAttempts(verification.getAttempts() + 1);
            if (verification.getAttempts() >= maxAttempts) {
                verification.setUsed(true);
                otpRepository.save(verification);
                log.warn("OTP verification failed: max attempts reached for {}", normalizedEmail);
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "OTP_MAX_ATTEMPTS: Nombre maximal de tentatives dépassé. Veuillez demander un nouveau code."
                );
            }
            otpRepository.save(verification);
            log.warn("OTP verification failed for {}", normalizedEmail);
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "OTP_INVALID: Code OTP invalide."
            );
        }

        // Success
        verification.setUsed(true);
        otpRepository.save(verification);
        log.info("OTP verification successful for {} ({})", normalizedEmail, type);
        return verification;
    }
}
