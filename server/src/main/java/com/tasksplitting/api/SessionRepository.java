package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
}
