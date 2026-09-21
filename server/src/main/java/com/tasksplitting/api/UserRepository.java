package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
            "SELECT id, username, passwordHash, \"createdAt\" FROM \"User\" WHERE username = ?",
            (rs, rowNum) -> new User(rs.getInt("id"), rs.getString("username"), rs.getString("passwordHash"), toLocalDateTime(rs.getTimestamp("createdAt"))),
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

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
