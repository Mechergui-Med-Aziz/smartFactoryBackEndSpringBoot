package com.smartfactory.service;

import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.OtpVerification;
import com.smartfactory.repository.OtpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpRepository otpRepository;

    private PasswordEncoder passwordEncoder;
    private OtpService otpService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        otpService = new OtpService(otpRepository, passwordEncoder, 10, 5, 60, 15);
    }

    @Test
    @DisplayName("Generate OTP creates a 6-digit code, hashes it, and saves verification")
    void testGenerateAndSaveOtp() {
        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());
        when(otpRepository.findByEmailAndType(any(), any())).thenReturn(List.of());

        String otp = otpService.generateAndSaveOtp("user@smartfactory.com", OtpType.REGISTER);

        assertThat(otp).hasSize(6).matches("\\d{6}");

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpRepository).save(captor.capture());

        OtpVerification saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@smartfactory.com");
        assertThat(saved.getType()).isEqualTo(OtpType.REGISTER);
        assertThat(passwordEncoder.matches(otp, saved.getOtpHash())).isTrue();
        assertThat(saved.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(9)));
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("Generate OTP invalidates existing unused OTPs of the same type")
    void testGenerateOtpInvalidatesPrevious() {
        OtpVerification oldOtp = new OtpVerification("user@smartfactory.com", "hash", OtpType.REGISTER, Instant.now().plusSeconds(300));
        oldOtp.setUsed(false);
        // Created more than 60s ago to bypass rate limit
        oldOtp.setCreatedAt(Instant.now().minusSeconds(120));

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.of(oldOtp));
        when(otpRepository.findByEmailAndType(any(), any())).thenReturn(List.of(oldOtp));

        otpService.generateAndSaveOtp("user@smartfactory.com", OtpType.REGISTER);

        assertThat(oldOtp.isUsed()).isTrue();
    }

    @Test
    @DisplayName("Generate OTP too soon throws OTP_RESEND_TOO_SOON (anti-spam)")
    void testGenerateOtpRateLimit() {
        OtpVerification recentOtp = new OtpVerification("user@smartfactory.com", "hash", OtpType.REGISTER, Instant.now().plusSeconds(500));
        recentOtp.setCreatedAt(Instant.now().minusSeconds(10)); // 10s ago < 60s

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(recentOtp));

        assertThatThrownBy(() -> otpService.generateAndSaveOtp("user@smartfactory.com", OtpType.REGISTER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getReason()).contains("OTP_RESEND_TOO_SOON");
                });
    }

    @Test
    @DisplayName("Verify OTP succeeds when code matches and is not expired")
    void testVerifyEmailOtpSuccess() {
        String rawOtp = "123456";
        String hash = passwordEncoder.encode(rawOtp);
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.REGISTER, Instant.now().plusSeconds(500));

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(verification));

        otpService.verifyEmailOtp("user@smartfactory.com", rawOtp);

        assertThat(verification.isUsed()).isTrue();
        verify(otpRepository).save(verification);
    }

    @Test
    @DisplayName("Verify OTP with wrong code increments attempts and throws OTP_INVALID")
    void testVerifyEmailOtpWrongCode() {
        String hash = passwordEncoder.encode("123456");
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.REGISTER, Instant.now().plusSeconds(500));
        verification.setAttempts(1);

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> otpService.verifyEmailOtp("user@smartfactory.com", "999999"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("OTP_INVALID"));

        assertThat(verification.getAttempts()).isEqualTo(2);
        assertThat(verification.isUsed()).isFalse();
        verify(otpRepository).save(verification);
    }

    @Test
    @DisplayName("Verify OTP exceeds max attempts marks OTP as used and throws OTP_MAX_ATTEMPTS")
    void testVerifyEmailOtpMaxAttemptsReached() {
        String hash = passwordEncoder.encode("123456");
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.REGISTER, Instant.now().plusSeconds(500));
        verification.setAttempts(4); // 5th attempt will exceed

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> otpService.verifyEmailOtp("user@smartfactory.com", "999999"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("OTP_MAX_ATTEMPTS"));

        assertThat(verification.getAttempts()).isEqualTo(5);
        assertThat(verification.isUsed()).isTrue();
    }

    @Test
    @DisplayName("Verify OTP throws OTP_EXPIRED when code expired")
    void testVerifyEmailOtpExpired() {
        String hash = passwordEncoder.encode("123456");
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.REGISTER, Instant.now().minusSeconds(10));

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> otpService.verifyEmailOtp("user@smartfactory.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("OTP_EXPIRED"));
    }

    @Test
    @DisplayName("Verify OTP throws OTP_ALREADY_USED when code was used")
    void testVerifyEmailOtpAlreadyUsed() {
        String hash = passwordEncoder.encode("123456");
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.REGISTER, Instant.now().plusSeconds(500));
        verification.setUsed(true);

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.REGISTER))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> otpService.verifyEmailOtp("user@smartfactory.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("OTP_ALREADY_USED"));
    }

    @Test
    @DisplayName("Verify reset OTP returns a valid reset token")
    void testVerifyResetOtpReturnsToken() {
        String rawOtp = "654321";
        String hash = passwordEncoder.encode(rawOtp);
        OtpVerification verification = new OtpVerification("user@smartfactory.com", hash, OtpType.RESET_PASSWORD, Instant.now().plusSeconds(500));

        when(otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("user@smartfactory.com", OtpType.RESET_PASSWORD))
                .thenReturn(Optional.of(verification));

        String resetToken = otpService.verifyResetOtp("user@smartfactory.com", rawOtp);

        assertThat(resetToken).isNotBlank();
        assertThat(verification.isUsed()).isTrue();
        assertThat(verification.getResetToken()).isEqualTo(resetToken);
        assertThat(verification.getResetTokenExpiresAt()).isAfter(Instant.now());
        assertThat(verification.isResetTokenUsed()).isFalse();
    }

    @Test
    @DisplayName("Validate and consume reset token succeeds then prevents reuse")
    void testValidateAndConsumeResetToken() {
        OtpVerification verification = new OtpVerification("user@smartfactory.com", "hash", OtpType.RESET_PASSWORD, Instant.now().plusSeconds(500));
        verification.setResetToken("sample-reset-token-123");
        verification.setResetTokenExpiresAt(Instant.now().plusSeconds(900));
        verification.setResetTokenUsed(false);

        when(otpRepository.findByEmailAndResetToken("user@smartfactory.com", "sample-reset-token-123"))
                .thenReturn(Optional.of(verification));

        // First consume succeeds
        otpService.validateAndConsumeResetToken("user@smartfactory.com", "sample-reset-token-123");
        assertThat(verification.isResetTokenUsed()).isTrue();

        // Second consume fails
        assertThatThrownBy(() -> otpService.validateAndConsumeResetToken("user@smartfactory.com", "sample-reset-token-123"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("RESET_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("Validate expired reset token throws RESET_TOKEN_EXPIRED")
    void testValidateExpiredResetToken() {
        OtpVerification verification = new OtpVerification("user@smartfactory.com", "hash", OtpType.RESET_PASSWORD, Instant.now().plusSeconds(500));
        verification.setResetToken("expired-token");
        verification.setResetTokenExpiresAt(Instant.now().minusSeconds(10)); // expired
        verification.setResetTokenUsed(false);

        when(otpRepository.findByEmailAndResetToken("user@smartfactory.com", "expired-token"))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> otpService.validateAndConsumeResetToken("user@smartfactory.com", "expired-token"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getReason()).contains("RESET_TOKEN_EXPIRED"));
    }
}
