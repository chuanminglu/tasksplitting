package com.tasksplitting.api;

/** Thrown when a login attempt fails with a client-visible authentication code. */
public class AuthException extends RuntimeException {
    private final String code;

    public AuthException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() { return code; }
}
