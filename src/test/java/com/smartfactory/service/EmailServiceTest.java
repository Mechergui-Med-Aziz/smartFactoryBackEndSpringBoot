package com.smartfactory.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, "noreply@smartfactory.com", 10);
    }

    @Test
    @DisplayName("Send verification email sends correct subject, recipient, and OTP message")
    void testSendVerificationEmail() {
        String to = "user@smartfactory.com";
        String otp = "123456";

        emailService.sendVerificationEmail(to, otp);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(Objects.requireNonNull(message.getTo())[0]).isEqualTo(to);
        assertThat(message.getFrom()).isEqualTo("noreply@smartfactory.com");
        assertThat(message.getSubject()).isEqualTo("SmartFactory - Verification de votre compte");
        assertThat(message.getText()).contains("123456");
        assertThat(message.getText()).contains("expire dans 10 minutes");
    }

    @Test
    @DisplayName("Send password reset email sends correct subject, recipient, and OTP message")
    void testSendPasswordResetEmail() {
        String to = "user@smartfactory.com";
        String otp = "987654";

        emailService.sendPasswordResetEmail(to, otp);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());

        SimpleMailMessage message = messageCaptor.getValue();
        assertThat(Objects.requireNonNull(message.getTo())[0]).isEqualTo(to);
        assertThat(message.getFrom()).isEqualTo("noreply@smartfactory.com");
        assertThat(message.getSubject()).isEqualTo("SmartFactory - Reinitialisation du mot de passe");
        assertThat(message.getText()).contains("987654");
        assertThat(message.getText()).contains("expire dans 10 minutes");
    }
}
