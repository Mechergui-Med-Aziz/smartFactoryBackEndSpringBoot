package com.smartfactory.dto.response;

import java.time.Instant;

public class ErrorResponse {
    private int status;
    private String code;
    private String message;
    private String timestamp;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = Instant.now().toString();
    }

    public ErrorResponse(int status, String code, String message, String timestamp) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
}
