package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Writes login-event telemetry rows.
 *
 * Callers (AuthService) wrap every call in try/catch so a DB failure
 * never blocks the auth flow.
 */
@Repository
public class LoginEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public LoginEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * INSERT one LoginEvent row.
     *
     * @param eventType one of LOGIN_SUCCESS, USER_NOT_FOUND, INVALID_PASSWORD, ACCOUNT_LOCKED
     * @param username  the username supplied in the request (may be empty or null)
     */
    public void record(String eventType, String username) {
        jdbcTemplate.update(
            "INSERT INTO \"LoginEvent\" (eventType, username) VALUES (?, ?)",
            eventType, username);
    }
}
