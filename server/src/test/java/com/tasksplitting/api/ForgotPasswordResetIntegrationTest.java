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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T00202（AC-2）：验证码校验 + 设置新密码。
 * [FACT] 10 分钟内正确验证码 + 新密码 → 200，User.passwordHash 更新（BCrypt），验证码 used=true。
 * [FACT] 验证码错误 → 拒绝，密码未变，验证码未用。
 * [FACT] 正确验证码超过 10 分钟（TestClock）→ 拒绝，密码未变。
 * [INFER] 成功使用一次后再用同一验证码 → 拒绝（used 生效）。
 * 边界：expiresAt 恰好等于当前时刻不算过期 → 仍有效。
 *
 * 使用独立 SQLite 库文件，避免与其他集成测试互相污染。
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-t00202.db"
})
@AutoConfigureMockMvc
class ForgotPasswordResetIntegrationTest {
    private static final String EMAIL = "reset@example.com";
    private static final String USERNAME = "resetuser";
    private static final String CODE = "123456";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @MockBean EmailSender emailSender;
    @Autowired @Qualifier("testClock") TestClock testClock;

    @BeforeAll
    static void wipeDatabase() throws Exception {
        Files.deleteIfExists(Path.of("target/test-t00202.db"));
    }

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM \"PasswordResetCode\"");
        jdbc.update("DELETE FROM \"User\"");
        testClock.reset();
    }

    @Test
    @DisplayName("10 分钟内正确验证码 → 200，密码已更新为 BCrypt(新密码)，验证码 used=true")
    void validCode_updatesPasswordAndMarksUsed() throws Exception {
        seedUser(ENCODER.encode("oldpass"));
        seedCode(CODE, "2026-01-01 00:10:00", false);

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists());

        String newHash = jdbc.queryForObject("SELECT passwordHash FROM \"User\" WHERE username=?", String.class, USERNAME);
        assertThat(ENCODER.matches("newpass", newHash)).isTrue();
        assertThat(ENCODER.matches("oldpass", newHash)).isFalse();
        assertThat(jdbc.queryForObject("SELECT used FROM \"PasswordResetCode\" WHERE code=?", Boolean.class, CODE)).isTrue();
    }

    @Test
    @DisplayName("验证码错误 → 401 拒绝，密码未变，验证码未用")
    void wrongCode_rejected_passwordUnchanged() throws Exception {
        String originalHash = ENCODER.encode("oldpass");
        seedUser(originalHash);
        seedCode(CODE, "2026-01-01 00:10:00", false);

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, "999999", "newpass"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_OR_EXPIRED_CODE"));

        assertThat(jdbc.queryForObject("SELECT passwordHash FROM \"User\" WHERE username=?", String.class, USERNAME)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("SELECT used FROM \"PasswordResetCode\" WHERE code=?", Boolean.class, CODE)).isFalse();
    }

    @Test
    @DisplayName("正确验证码但超过 10 分钟（TestClock 推进）→ 401 拒绝，密码未变，验证码未用")
    void expiredCode_rejected_passwordUnchanged() throws Exception {
        String originalHash = ENCODER.encode("oldpass");
        seedUser(originalHash);
        seedCode(CODE, "2026-01-01 00:05:00", false); // 5 分钟后过期
        testClock.advance(Duration.ofMinutes(11));      // 当前时刻已越过 expiresAt

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_OR_EXPIRED_CODE"));

        assertThat(jdbc.queryForObject("SELECT passwordHash FROM \"User\" WHERE username=?", String.class, USERNAME)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("SELECT used FROM \"PasswordResetCode\" WHERE code=?", Boolean.class, CODE)).isFalse();
    }

    @Test
    @DisplayName("成功使用一次后，同一验证码再次提交 → 拒绝（used 生效）")
    void usedCode_rejectedOnSecondUse() throws Exception {
        seedUser(ENCODER.encode("oldpass"));
        seedCode(CODE, "2026-01-01 00:10:00", false);

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "another"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_OR_EXPIRED_CODE"));
    }

    @Test
    @DisplayName("边界：expiresAt 恰好等于当前时刻 → 不算过期，仍有效")
    void expiresAtExactlyNow_stillValid() throws Exception {
        seedUser(ENCODER.encode("oldpass"));
        seedCode(CODE, "2026-01-01 00:00:00", false); // 恰好等于 base 时刻

        mockMvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content(objectMapper.writeValueAsString(new ResetPasswordRequest(EMAIL, CODE, "newpass"))))
            .andExpect(status().isOk());

        String newHash = jdbc.queryForObject("SELECT passwordHash FROM \"User\" WHERE username=?", String.class, USERNAME);
        assertThat(ENCODER.matches("newpass", newHash)).isTrue();
    }

    private void seedUser(String passwordHash) {
        jdbc.update("INSERT INTO \"User\" (username, passwordHash, email) VALUES (?, ?, ?)",
            USERNAME, passwordHash, EMAIL);
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
        void advance(Duration duration) { instant.updateAndGet(current -> current.plus(duration)); }
        void reset() { instant.set(BASE_INSTANT); }
    }
}
