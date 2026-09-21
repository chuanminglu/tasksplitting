package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Repository
public class UserRepository {
    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureLoginColumns();
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
            "SELECT id, username, passwordHash, \"createdAt\", failedLoginAttempts, lockedUntil FROM \"User\" WHERE username = ?",
            (rs, rowNum) -> new User(
                rs.getInt("id"),
                rs.getString("username"),
                rs.getString("passwordHash"),
                toLocalDateTime(rs.getTimestamp("createdAt")),
                rs.getInt("failedLoginAttempts"),
                toLocalDateTime(rs.getTimestamp("lockedUntil"))),
            username
        ).stream().findFirst();
    }

    public User create(String username, String passwordHash) {
        Integer id = jdbcTemplate.queryForObject(
            "INSERT INTO \"User\" (username, passwordHash) VALUES (?, ?) RETURNING id",
            Integer.class, username, passwordHash);
        return findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("user not found after insert: " + username));
    }

    public void updateLoginFailure(int userId, int failedLoginAttempts, LocalDateTime lockedUntil) {
        jdbcTemplate.update(
            "UPDATE \"User\" SET failedLoginAttempts = ?, lockedUntil = ? WHERE id = ?",
            failedLoginAttempts, formatDateTime(lockedUntil), userId);
    }

    public void resetLoginFailures(int userId) {
        jdbcTemplate.update(
            "UPDATE \"User\" SET failedLoginAttempts = 0, lockedUntil = NULL WHERE id = ?",
            userId);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private void ensureLoginColumns() {
        Set<String> columns = new HashSet<>(jdbcTemplate.query(
            "PRAGMA table_info(\"User\")",
            (rs, rowNum) -> rs.getString("name")));
        if (!columns.contains("failedLoginAttempts")) {
            jdbcTemplate.execute("ALTER TABLE \"User\" ADD COLUMN failedLoginAttempts INTEGER NOT NULL DEFAULT 0");
        }
        if (!columns.contains("lockedUntil")) {
            jdbcTemplate.execute("ALTER TABLE \"User\" ADD COLUMN lockedUntil DATETIME");
        }
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(SQL_DATE_TIME);
    }
}
