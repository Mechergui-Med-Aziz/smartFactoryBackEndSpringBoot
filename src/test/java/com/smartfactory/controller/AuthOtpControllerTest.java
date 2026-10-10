package com.smartfactory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfactory.entity.OtpType;
import com.smartfactory.entity.OtpVerification;
import com.smartfactory.entity.User;
import com.smartfactory.repository.OtpRepository;
import com.smartfactory.repository.UserRepository;
import com.smartfactory.security.Role;
import com.smartfactory.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthOtpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private org.springframework.mail.javamail.JavaMailSender mailSender;

    @BeforeEach
    void setUp() {
        otpRepository.deleteAll();
        userRepository.deleteAll();
        org.mockito.Mockito.doNothing().when(mailSender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    @DisplayName("REGISTER: Creates unverified user and sends OTP email; login blocked until verified")
    void testRegisterFlow() throws Exception {
        Map<String, Object> registerReq = Map.of(
                "firstName", "Alice",
                "lastName", "Smith",
                "email", "alice@smartfactory.com",
                "password", "Password123!",
                "role", Role.OPERATOR,
                "status", "ACTIVE"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message", is("Compte créé. Un code de vérification a été envoyé à votre adresse e-mail.")));

        // Verify user exists and is unverified
        User user = userRepository.findByEmail("alice@smartfactory.com").orElseThrow();
        assertThat(user.isEmailVerified()).isFalse();

        // Verify OTP was created
        OtpVerification otp = otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("alice@smartfactory.com", OtpType.REGISTER).orElseThrow();
        assertThat(otp.getOtpHash()).isNotBlank();
        assertThat(otp.isUsed()).isFalse();

        // Attempting to login before verification returns 403 Forbidden
        Map<String, String> loginReq = Map.of("email", "alice@smartfactory.com", "password", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("EMAIL_NOT_VERIFIED")));
    }

    @Test
    @DisplayName("REGISTER: Registering with an existing email returns 409 CONFLICT")
    void testRegisterDuplicateEmail() throws Exception {
        User existing = new User("Bob", "Brown", "bob@smartfactory.com", passwordEncoder.encode("Pass123!"), Role.OPERATOR, "ACTIVE");
        userRepository.save(existing);

        Map<String, Object> registerReq = Map.of(
                "firstName", "Bob",
                "lastName", "Brown",
                "email", "bob@smartfactory.com",
                "password", "Pass123!",
                "role", Role.OPERATOR,
                "status", "ACTIVE"
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_ALREADY_EXISTS")));
    }

    @Test
    @DisplayName("VERIFY EMAIL: Successfully verifies email and enables login")
    void testVerifyEmailSuccess() throws Exception {
        // Create unverified user and OTP
        User user = new User("Charlie", "Chaplin", "charlie@smartfactory.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(false);
        userRepository.save(user);

        String rawOtp = "123456";
        OtpVerification otpVerification = new OtpVerification("charlie@smartfactory.com", passwordEncoder.encode(rawOtp), OtpType.REGISTER, Instant.now().plusSeconds(600));
        otpRepository.save(otpVerification);

        // Verify email
        Map<String, String> verifyReq = Map.of("email", "charlie@smartfactory.com", "otp", rawOtp);
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Adresse e-mail vérifiée avec succès.")));

        // User is now verified
        User updated = userRepository.findByEmail("charlie@smartfactory.com").orElseThrow();
        assertThat(updated.isEmailVerified()).isTrue();

        // Login now succeeds
        Map<String, String> loginReq = Map.of("email", "charlie@smartfactory.com", "password", "Password123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
    }

    @Test
    @DisplayName("VERIFY EMAIL: Wrong OTP returns 400 OTP_INVALID")
    void testVerifyEmailWrongOtp() throws Exception {
        User user = new User("Dave", "D", "dave@smartfactory.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(false);
        userRepository.save(user);

        OtpVerification otpVerification = new OtpVerification("dave@smartfactory.com", passwordEncoder.encode("123456"), OtpType.REGISTER, Instant.now().plusSeconds(600));
        otpRepository.save(otpVerification);

        Map<String, String> verifyReq = Map.of("email", "dave@smartfactory.com", "otp", "999999");
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("OTP_INVALID")));
    }

    @Test
    @DisplayName("VERIFY EMAIL: Expired OTP returns 400 OTP_EXPIRED")
    void testVerifyEmailExpiredOtp() throws Exception {
        User user = new User("Eve", "E", "eve@smartfactory.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(false);
        userRepository.save(user);

        OtpVerification otpVerification = new OtpVerification("eve@smartfactory.com", passwordEncoder.encode("123456"), OtpType.REGISTER, Instant.now().minusSeconds(10));
        otpRepository.save(otpVerification);

        Map<String, String> verifyReq = Map.of("email", "eve@smartfactory.com", "otp", "123456");
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("OTP_EXPIRED")));
    }

    @Test
    @DisplayName("VERIFY EMAIL: Already verified user returns 400 BAD_REQUEST")
    void testVerifyEmailAlreadyVerified() throws Exception {
        User user = new User("Frank", "F", "frank@smartfactory.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(true);
        userRepository.save(user);

        Map<String, String> verifyReq = Map.of("email", "frank@smartfactory.com", "otp", "123456");
        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("RESEND: Resend verification creates new OTP and respects anti-spam delay")
    void testResendVerificationFlow() throws Exception {
        User user = new User("Grace", "G", "grace@smartfactory.com", passwordEncoder.encode("Password123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(false);
        userRepository.save(user);

        // Previous OTP created 70 seconds ago (> 60s delay)
        OtpVerification oldOtp = new OtpVerification("grace@smartfactory.com", passwordEncoder.encode("111111"), OtpType.REGISTER, Instant.now().plusSeconds(300));
        oldOtp.setCreatedAt(Instant.now().minusSeconds(70));
        otpRepository.save(oldOtp);

        Map<String, String> resendReq = Map.of("email", "grace@smartfactory.com");
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Un nouveau code de vérification a été envoyé à votre adresse e-mail.")));

        // Immediate retry hits anti-spam rate limit (429)
        mockMvc.perform(post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resendReq)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code", is("OTP_RESEND_TOO_SOON")));
    }

    @Test
    @DisplayName("FORGOT & RESET PASSWORD: Complete flow with OTP verification and password update")
    void testForgotAndResetPasswordFlow() throws Exception {
        // User with known password
        User user = new User("Heidi", "H", "heidi@smartfactory.com", passwordEncoder.encode("OldPassword123!"), Role.OPERATOR, "ACTIVE");
        user.setEmailVerified(true);
        userRepository.save(user);

        // 1. Forgot password
        Map<String, String> forgotReq = Map.of("email", "heidi@smartfactory.com");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Si un compte correspond à cette adresse e-mail, un code de réinitialisation a été envoyé.")));

        OtpVerification resetOtp = otpRepository.findTopByEmailAndTypeOrderByCreatedAtDesc("heidi@smartfactory.com", OtpType.RESET_PASSWORD).orElseThrow();

        // 2. Fast forward to avoid code generation dependency: inject a known OTP
        resetOtp.setOtpHash(passwordEncoder.encode("654321"));
        otpRepository.save(resetOtp);

        // 3. Verify Reset OTP
        Map<String, String> verifyResetReq = Map.of("email", "heidi@smartfactory.com", "otp", "654321");
        String responseContent = mockMvc.perform(post("/api/auth/verify-reset-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyResetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Code OTP vérifié avec succès.")))
                .andExpect(jsonPath("$.resetToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String resetToken = objectMapper.readTree(responseContent).get("resetToken").asText();

        // 4. Reset Password
        Map<String, String> resetPassReq = Map.of("email", "heidi@smartfactory.com", "resetToken", resetToken, "newPassword", "NewSecurePassword456!");
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPassReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Mot de passe réinitialisé avec succès.")));

        // 5. Old password no longer works
        Map<String, String> oldLoginReq = Map.of("email", "heidi@smartfactory.com", "password", "OldPassword123!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(oldLoginReq)))
                .andExpect(status().isUnauthorized());

        // 6. New password works
        Map<String, String> newLoginReq = Map.of("email", "heidi@smartfactory.com", "password", "NewSecurePassword456!");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));

        // 7. Reset token cannot be reused
        mockMvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetPassReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("RESET_TOKEN_INVALID")));
    }

    @Test
    @DisplayName("FORGOT PASSWORD: Non-existent email returns generic message without error")
    void testForgotPasswordNonExistentEmail() throws Exception {
        Map<String, String> forgotReq = Map.of("email", "nonexistent@smartfactory.com");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Si un compte correspond à cette adresse e-mail, un code de réinitialisation a été envoyé.")));
    }
}
