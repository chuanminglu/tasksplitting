package com.tasksplitting.api;

/** Thrown for any login failure in this phase; error-code refinement lands in T00102. */
public class AuthException extends RuntimeException {
    public AuthException(String message) { super(message); }
}
