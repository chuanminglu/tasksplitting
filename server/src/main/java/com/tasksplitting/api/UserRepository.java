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
        ensureEmailColumn();
        ensureAvatarColumn();
    }

    public Optional<User> findByUsername(String username) {
        return jdbcTemplate.query(
            "SELECT id, username, passwordHash, email, \"createdAt\", failedLoginAttempts, lockedUntil, avatarUrl FROM \"User\" WHERE username = ?",
            (rs, rowNum) -> toUser(rs),
            username
        ).stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        return jdbcTemplate.query(
            "SELECT id, username, passwordHash, email, \"createdAt\", failedLoginAttempts, lockedUntil, avatarUrl FROM \"User\" WHERE email = ?",
            (rs, rowNum) -> toUser(rs),
            email
        ).stream().findFirst();
    }

    public Optional<User> findById(int id) {
        return jdbcTemplate.query(
            "SELECT id, username, passwordHash, email, \"createdAt\", failedLoginAttempts, lockedUntil, avatarUrl FROM \"User\" WHERE id = ?",
            (rs, rowNum) -> toUser(rs),
            id
        ).stream().findFirst();
    }

    private User toUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new User(
            rs.getInt("id"),
            rs.getString("username"),
            rs.getString("passwordHash"),
            rs.getString("email"),
            toLocalDateTime(rs.getTimestamp("createdAt")),
            rs.getInt("failedLoginAttempts"),
            toLocalDateTime(rs.getTimestamp("lockedUntil")),
            rs.getString("avatarUrl"));
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

    /** T00202（AC-2）：更新用户密码哈希（忘记密码-设置新密码）。 */
    public void updatePasswordHash(int userId, String passwordHash) {
        jdbcTemplate.update("UPDATE \"User\" SET passwordHash = ? WHERE id = ?", passwordHash, userId);
    }

    /**
     * T00301（AC-1）：更新用户的头像 URL（上传新头像后写入 {@code User.avatarUrl}）。
     * 传入 {@code null} 可清除当前头像。
     */
    public void updateAvatarUrl(int userId, String avatarUrl) {
        jdbcTemplate.update("UPDATE \"User\" SET avatarUrl = ? WHERE id = ?", avatarUrl, userId);
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

    /**
     * T00301: avatarUrl 列迁移（头像上传写入 {@code User.avatarUrl}）。
     * 沿用 T00201 email 列的 PRAGMA 探测 + 条件 ALTER 模式，
     * 因为 schema.sql 每次启动都重放（{@code sql.init.mode=always}），裸 ALTER 会失败。
     */
    private void ensureAvatarColumn() {
        Set<String> columns = new HashSet<>(jdbcTemplate.query(
            "PRAGMA table_info(\"User\")",
            (rs, rowNum) -> rs.getString("name")));
        if (!columns.contains("avatarUrl")) {
            jdbcTemplate.execute("ALTER TABLE \"User\" ADD COLUMN avatarUrl TEXT");
        }
    }

    /**
     * T00201: email 列迁移（找回密码按邮箱定位用户）。SQLite 不支持
     * "ADD COLUMN IF NOT EXISTS"，沿用 T00103 的 PRAGMA 探测 + 条件 ALTER 模式。
     * schema.sql 每次启动都重放（sql.init.mode=always），因此 ALTER 不能放 schema.sql。
     */
    private void ensureEmailColumn() {
        Set<String> columns = new HashSet<>(jdbcTemplate.query(
            "PRAGMA table_info(\"User\")",
            (rs, rowNum) -> rs.getString("name")));
        if (!columns.contains("email")) {
            jdbcTemplate.execute("ALTER TABLE \"User\" ADD COLUMN email TEXT");
        }
    }

    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(SQL_DATE_TIME);
    }
}
