package com.tasksplitting.api;

import java.time.LocalDateTime;

/** Thrown when a login attempt fails with a client-visible authentication code. */
public class AuthException extends RuntimeException {
    private final String code;
    private final LocalDateTime lockedUntil;

    public AuthException(String code, String message) {
        this(code, message, null);
    }

    public AuthException(String code, String message, LocalDateTime lockedUntil) {
        super(message);
        this.code = code;
        this.lockedUntil = lockedUntil;
    }

    public String getCode() { return code; }

    public LocalDateTime getLockedUntil() { return lockedUntil; }
}
