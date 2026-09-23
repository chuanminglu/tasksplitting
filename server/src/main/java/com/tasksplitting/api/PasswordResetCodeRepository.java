package com.tasksplitting.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Repository
public class PasswordResetCodeRepository {
    // 与 UserRepository/SessionRepository 一致：SQLite JDBC 的日期解析只认该格式
    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;

    public PasswordResetCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 生成新的验证码记录（used 默认 FALSE）。 */
    public PasswordResetCode insert(String email, String code, LocalDateTime expiresAt) {
        Integer id = jdbcTemplate.queryForObject(
            "INSERT INTO \"PasswordResetCode\" (email, code, expiresAt) VALUES (?, ?, ?) RETURNING id",
            Integer.class, email, code, expiresAt.format(SQL_DATE_TIME));
        return findByEmailAndCode(email, code)
            .orElseThrow(() -> new IllegalStateException("reset code not found after insert"));
    }

    public Optional<PasswordResetCode> findByEmailAndCode(String email, String code) {
        return jdbcTemplate.query(
            "SELECT id, email, code, expiresAt, used, createdAt FROM \"PasswordResetCode\" WHERE email = ? AND code = ?",
            (rs, rowNum) -> toCode(rs),
            email, code
        ).stream().findFirst();
    }

    private PasswordResetCode toCode(ResultSet rs) throws SQLException {
        return new PasswordResetCode(
            rs.getInt("id"),
            rs.getString("email"),
            rs.getString("code"),
            rs.getTimestamp("expiresAt") == null ? null : rs.getTimestamp("expiresAt").toLocalDateTime(),
            rs.getBoolean("used"),
            rs.getTimestamp("createdAt") == null ? null : rs.getTimestamp("createdAt").toLocalDateTime());
    }
}
