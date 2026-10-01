package com.smartfactory.dto.response;

public class VerifyResetOtpResponse {

    private String message;
    private String resetToken;

    public VerifyResetOtpResponse() {
    }

    public VerifyResetOtpResponse(String message, String resetToken) {
        this.message = message;
        this.resetToken = resetToken;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}
