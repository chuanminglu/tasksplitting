package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Repository
public class SessionRepository {
    /**
     * SQLite JDBC's date parser only accepts the "yyyy-MM-dd HH:mm:ss" form
     * (the shape used by {@code CURRENT_TIMESTAMP}); binding a raw {@link LocalDateTime}
     * stores ISO-8601 with a "T" separator, which fails to parse back on read.
     */
    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void create(String token, int userId, LocalDateTime expiresAt) {
        jdbcTemplate.update(
            "INSERT INTO \"Session\" (token, userId, expiresAt) VALUES (?, ?, ?)",
            token, userId, expiresAt.format(SQL_DATE_TIME));
    }

    /**
     * Look up a session by its opaque token.
     *
     * @param token the bearer token from the Authorization header
     * @return the stored token and its expiry, or empty if not found
     */
    public Optional<StoredSession> findByToken(String token) {
        return jdbcTemplate.query(
            "SELECT token, userId, expiresAt FROM \"Session\" WHERE token = ?",
            (rs, rowNum) -> new StoredSession(
                rs.getString("token"),
                rs.getInt("userId"),
                rs.getTimestamp("expiresAt").toLocalDateTime()),
            token
        ).stream().findFirst();
    }

    /**
     * Delete all sessions belonging to a single user. Used after a password reset so that
     * every pre-reset session for that user is immediately invalidated (AC-3).
     *
     * @param userId the id of the user whose sessions should be invalidated
     */
    public void deleteAllForUser(int userId) {
        jdbcTemplate.update("DELETE FROM \"Session\" WHERE userId = ?", userId);
    }

    /** A session row read from the database. */
    public record StoredSession(String token, int userId, LocalDateTime expiresAt) {}
}
