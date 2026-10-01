package com.smartfactory.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "otp_verifications")
@CompoundIndex(name = "email_type_idx", def = "{'email': 1, 'type': 1}")
public class OtpVerification {

    @Id
    private String id;

    @Indexed
    private String email;

    private String otpHash;

    private OtpType type;

    private Instant expiresAt;

    private boolean used = false;

    private int attempts = 0;

    private Instant createdAt = Instant.now();

    // Reset password token (generated upon reset OTP verification)
    private String resetToken;
    private Instant resetTokenExpiresAt;
    private boolean resetTokenUsed = false;

    public OtpVerification() {
    }

    public OtpVerification(String email, String otpHash, OtpType type, Instant expiresAt) {
        this.email = email;
        this.otpHash = otpHash;
        this.type = type;
        this.expiresAt = expiresAt;
        this.used = false;
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getOtpHash() {
        return otpHash;
    }

    public void setOtpHash(String otpHash) {
        this.otpHash = otpHash;
    }

    public OtpType getType() {
        return type;
    }

    public void setType(OtpType type) {
        this.type = type;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    public Instant getResetTokenExpiresAt() {
        return resetTokenExpiresAt;
    }

    public void setResetTokenExpiresAt(Instant resetTokenExpiresAt) {
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    public boolean isResetTokenUsed() {
        return resetTokenUsed;
    }

    public void setResetTokenUsed(boolean resetTokenUsed) {
        this.resetTokenUsed = resetTokenUsed;
    }
}
