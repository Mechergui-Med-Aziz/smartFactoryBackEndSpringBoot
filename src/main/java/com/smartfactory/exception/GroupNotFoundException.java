package com.smartfactory.exception;

import org.springframework.http.HttpStatus;

public class GroupNotFoundException extends ApiException {
    public GroupNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.GROUP_NOT_FOUND, message);
    }
}
