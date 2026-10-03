package com.smartfactory.exception;

import org.springframework.http.HttpStatus;

public class InvalidGroupException extends ApiException {
    public InvalidGroupException(ErrorCode code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
