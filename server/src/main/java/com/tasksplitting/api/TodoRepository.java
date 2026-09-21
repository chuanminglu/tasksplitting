package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class TodoRepository {
    private final JdbcTemplate jdbcTemplate;

    public TodoRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<Todo> findAll() {
        return jdbcTemplate.query(
            "SELECT id, title, completed, \"createdAt\" FROM \"Todo\" ORDER BY \"createdAt\" DESC",
            (rs, rowNum) -> new Todo(rs.getInt("id"), rs.getString("title"), rs.getBoolean("completed"), toLocalDateTime(rs.getTimestamp("createdAt")))
        );
    }

    public Todo create(String title) {
        Integer id = jdbcTemplate.queryForObject(
            "INSERT INTO \"Todo\" (title, completed, \"createdAt\") VALUES (?, 0, CURRENT_TIMESTAMP) RETURNING id",
            Integer.class, title);
        return jdbcTemplate.queryForObject(
            "SELECT id, title, completed, \"createdAt\" FROM \"Todo\" WHERE id = ?",
            (rs, rowNum) -> new Todo(rs.getInt("id"), rs.getString("title"), rs.getBoolean("completed"), toLocalDateTime(rs.getTimestamp("createdAt"))), id);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
