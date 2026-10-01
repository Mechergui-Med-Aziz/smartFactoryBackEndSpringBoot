package com.smartfactory.exception;

public enum ErrorCode {
    // Auth & Users
    INVALID_CREDENTIALS,
    USER_NOT_FOUND,
    USER_ALREADY_EXISTS,
    UNAUTHORIZED,
    FORBIDDEN,
    INVALID_TOKEN,
    TOKEN_EXPIRED,
    USER_INACTIVE,

    // Machines
    MACHINE_NOT_FOUND,
    MACHINE_ALREADY_EXISTS,
    INVALID_MACHINE_STATUS,

    // Zones
    ZONE_NOT_FOUND,
    ZONE_ALREADY_EXISTS,

    // Validation & Generic
    VALIDATION_ERROR,
    BAD_REQUEST,
    INTERNAL_SERVER_ERROR
}
