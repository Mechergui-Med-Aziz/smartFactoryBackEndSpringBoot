package com.smartfactory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final int expirationMinutes;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:noreply@smartfactory.com}") String fromEmail,
                        @Value("${otp.expiration-minutes:10}") int expirationMinutes) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.expirationMinutes = expirationMinutes;
    }

    public void sendVerificationEmail(String toEmail, String otp) {
        log.info("Verification email requested for {}", toEmail);
        String subject = "SmartFactory - Verification de votre compte";
        String content = "Bonjour,\n\n"
                + "Votre code de verification SmartFactory est :\n\n"
                + otp + "\n\n"
                + "Ce code expire dans " + expirationMinutes + " minutes.\n\n"
                + "Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.\n\n"
                + "Cordialement,\n"
                + "SmartFactory";

        sendEmail(toEmail, subject, content);
    }

    public void sendPasswordResetEmail(String toEmail, String otp) {
        log.info("Password reset requested for {}", toEmail);
        String subject = "SmartFactory - Reinitialisation du mot de passe";
        String content = "Bonjour,\n\n"
                + "Vous avez demandé la reinitialisation de votre mot de passe SmartFactory.\n\n"
                + "Votre code OTP est :\n\n"
                + otp + "\n\n"
                + "Ce code expire dans " + expirationMinutes + " minutes.\n\n"
                + "Si vous n'etes pas a l'origine de cette demande, ignorez cet e-mail.\n\n"
                + "Cordialement,\n"
                + "SmartFactory";

        sendEmail(toEmail, subject, content);
    }

    private void sendEmail(String toEmail, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}", toEmail, e);
            throw e;
        }
    }
}
