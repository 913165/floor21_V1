package com.floor21.exception;

public class UnauthorizedTenantException extends RuntimeException {

    public UnauthorizedTenantException(String message) {
        super(message);
    }
}
