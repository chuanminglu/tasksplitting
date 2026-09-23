package com.tasksplitting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T00203（AC-3）：旧密码失效与重新登录约束。
 * 本任务在 T00202 已实现的"新密码更新"之上，额外保证"旧会话立即失效"：
 * 密码重置成功后，该用户此前所有 Session 被删除，旧 token 访问受保护接口 → 401。
 *
 * [FACT]  密码重置成功后，用旧密码登录 → 401 + INVALID_PASSWORD（T00202 已保证，本测试补断言）
 * [FACT]  密码重置成功后，用新密码登录 → 200 + token
 * [INFER] 密码重置前已存在的有效 Session，重置后携带该 token 访问 /api/todos → 401（会话已失效）
 * 边界   其他用户的 Session 不受本次重置影响（deleteAllForUser 严格限定在触发重置的 userId）
 *
 * 使用独立 SQLite 库文件，避免与其他集成测试互相污染。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-t00203.db"
})
@AutoConfigureMockMvc
class ForgotPasswordSessionInvalidationIntegrationTest {
    private static final String USERNAME = "t00203user";
    private static final String EMAIL = "t00203@example.com";
    private static final String CODE = "123456";
    private static final String OLD_TOKEN = "old-session-token-t00203";
    private static final String OTHER_USER_TOKEN = "other-user-token-t00203";
    private static final String OTHER_USERNAME = "other-user-t00203";
    private static final String OTHER_EMAIL = "other-user-t00203@example.com";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockBean EmailSender emailSender;
    @Autowired @Qualifier("testClock") TestClock testClock;

    @BeforeAll
    static void wipeDatabase() throws Exception {
        Files.deleteIfExists(Path.of("target/test-t00203.db"));
    }

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM \"PasswordResetCode\"");
        jdbc.update("DELETE FROM \"Session\"");
        jdbc.update("DELETE FROM \"LoginEvent\"");
        jdbc.update("DELETE FROM \"User\"");
        jdbc.update("DELETE FROM \"Todo\"");
        testClock.reset();
    }

    @Test
    @DisplayName("密码重置成功后：旧密码登录 → 401 INVALID_PASSWORD，新密码登录 → 200 + token")
    void oldPasswordInvalidAndNewPasswordWorks() throws Exception {
        seedUser(1L, USERNAME, ENCODER.encode("oldpass"), EMAIL);
        seedCode(CODE, "2026-01-01 00:10:00", false);

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk());

        // 旧密码登录 → 401 + INVALID_PASSWORD
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content(objectMapper.writeValueAsString(new LoginRequest(USERNAME, "oldpass", false))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_PASSWORD"));

        // 新密码登录 → 200 + token
        String body = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content(objectMapper.writeValueAsString(new LoginRequest(USERNAME, "newpass", false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("\"token\"");
    }

    @Test
    @DisplayName("密码重置前已登录产生的旧 Session，重置后携带旧 token 访问受保护接口 → 401")
    void oldSessionInvalidatedAfterReset() throws Exception {
        seedUser(1L, USERNAME, ENCODER.encode("oldpass"), EMAIL);
        // 模拟重置前已登录：一条未过期的 Session（token = OLD_TOKEN）
        seedSession(OLD_TOKEN, 1L, "2026-01-01 02:00:00");
        seedCode(CODE, "2026-01-01 00:10:00", false);

        // 重置前先确认旧 token 仍然有效（对照组）
        mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + OLD_TOKEN))
            .andExpect(status().isOk());

        // 执行重置
        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk());

        // 重置后：旧 token 已被删除 → 401
        mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + OLD_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // DB 层面确认该 token 已不存在
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"Session\" WHERE token = ?", Integer.class, OLD_TOKEN);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("其他用户的 Session 不受本次重置影响（deleteAllForUser 严格限定 userId）")
    void otherUserSessionUnaffectedByReset() throws Exception {
        seedUser(1L, USERNAME, ENCODER.encode("oldpass"), EMAIL);
        seedUser(2L, OTHER_USERNAME, ENCODER.encode("otherpass"), OTHER_EMAIL);
        // 两位用户各自已登录，都有未过期的 Session
        seedSession(OLD_TOKEN, 1L, "2026-01-01 02:00:00");
        seedSession(OTHER_USER_TOKEN, 2L, "2026-01-01 02:00:00");
        seedCode(CODE, "2026-01-01 00:10:00", false);

        // 仅 user1（触发重置的那个用户）执行重置
        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk());

        // user1 的旧 Session 已被删除
        Integer user1Sessions = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"Session\" WHERE userId = 1", Integer.class);
        assertThat(user1Sessions).isZero();

        // user2 的 Session 完整保留，旧 token 仍可用于访问受保护接口
        Integer user2Sessions = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"Session\" WHERE userId = 2", Integer.class);
        assertThat(user2Sessions).isEqualTo(1);
        mockMvc.perform(get("/api/todos").header("Authorization", "Bearer " + OTHER_USER_TOKEN))
            .andExpect(status().isOk());
    }

    // ---------- seeding ----------

    private void seedUser(long id, String username, String passwordHash, String email) {
        jdbc.update("INSERT INTO \"User\" (id, username, passwordHash, email) VALUES (?, ?, ?, ?)",
            id, username, passwordHash, email);
    }

    private void seedSession(String token, long userId, String expiresAt) {
        jdbc.update("INSERT INTO \"Session\" (token, userId, expiresAt) VALUES (?, ?, ?)",
            token, userId, expiresAt);
    }

    private void seedCode(String code, String expiresAt, boolean used) {
        jdbc.update("INSERT INTO \"PasswordResetCode\" (email, code, expiresAt, used) VALUES (?, ?, ?, ?)",
            EMAIL, code, expiresAt, used);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {
        @Bean
        @Primary
        TestClock testClock() { return new TestClock(); }
    }

    static class TestClock extends Clock {
        static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
        private final AtomicReference<Instant> instant = new AtomicReference<>(BASE_INSTANT);
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant.get(); }
        void reset() { instant.set(BASE_INSTANT); }
    }
}
