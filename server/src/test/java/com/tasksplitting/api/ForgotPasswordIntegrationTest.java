package com.tasksplitting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T00201（AC-1）DoD 验收测试：
 * 1. 已注册邮箱 → 200 + PasswordResetCode 新增一条，code 6 位数字，expiresAt ≈ now+10min
 * 2. 未注册邮箱 → 200 + 响应体结构相同 + 表无新记录
 * 3. EmailSender bean 被调用且参数含收件邮箱与验证码（不解析日志）
 * 4. 验证码不出现在 HTTP 响应体
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:sqlite:target/test-forgot-password.db"})
@AutoConfigureMockMvc
class ForgotPasswordIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EmailSender emailSender;

    @Autowired
    @Qualifier("testClock") private TestClock clock;

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void resetClockAndClean() {
        clock.reset();
        jdbc.update("DELETE FROM \"PasswordResetCode\"");
    }

    @Test
    void requestReset_forRegisteredEmail_returns200AndCreates6DigitCodeExpiringIn10Minutes() throws Exception {
        int rowsBefore = countResetCodes();
        String email = "alice@example.com";
        seedUserWithEmail("alice", email);

        String body = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString();

        int rowsAfter = countResetCodes();
        assertEquals(rowsBefore + 1, rowsAfter, "应恰好新增一条验证码记录");

        List<String> codes = jdbc.query(
                "SELECT code FROM \"PasswordResetCode\" WHERE email = ?", (rs, n) -> rs.getString("code"), email);
        assertEquals(1, codes.size());
        String code = codes.get(0);
        assertTrue(code.matches("\\d{6}"), "code 必须是 6 位数字，实际: " + code);

        // expiresAt ≈ clock.now() + 10min（SQL 日期格式只到秒，容差 2 秒）
        String expiresAtStr = jdbc.queryForObject(
                "SELECT expiresAt FROM \"PasswordResetCode\" WHERE email = ?", String.class, email);
        java.time.LocalDateTime expiresAt = java.time.LocalDateTime.parse(expiresAtStr, SQL_DATE_TIME);
        java.time.LocalDateTime expected = java.time.LocalDateTime.ofInstant(clock.now(), java.time.ZoneOffset.UTC)
                .plusMinutes(ForgotPasswordService.CODE_VALIDITY_MINUTES);
        assertTrue(Duration.between(expiresAt, expected).abs().compareTo(Duration.ofSeconds(2)) <= 0,
                "expiresAt 应约为 now+10min: 实际=" + expiresAt + " 期望≈" + expected);

        // 验证码不出现在 HTTP 响应体
        assertFalse(body.contains(code), "验证码绝不能出现在响应体中");
    }

    @Test
    void requestReset_forUnregisteredEmail_returnsSame200BodyWithoutCreatingRecordOrSendingMail() throws Exception {
        String unknownEmail = "ghost@example.com";
        int rowsBefore = countResetCodes();

        String body = mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + unknownEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString();

        assertEquals(rowsBefore, countResetCodes(), "未注册邮箱不得新增验证码记录");
        verify(emailSender, never()).send(anyString(), anyString(), anyString());

        // 响应体结构与已注册场景完全一致（仅 message 键，值相同）
        JsonNode unregistered = objectMapper.readTree(body);
        assertEquals(1, unregistered.size());
        assertEquals(ForgotPasswordController.RESET_MESSAGE, unregistered.get("message").asText());
    }

    @Test
    void requestReset_forRegisteredEmail_sendsEmailToRecipientWith6DigitCodeInBody() throws Exception {
        String email = "bob@example.com";
        seedUserWithEmail("bob", email);

        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());

        String codeInDb = jdbc.queryForObject(
                "SELECT code FROM \"PasswordResetCode\" WHERE email = ?", String.class, email);

        // DoD#3：mock 验证被调用且参数含收件邮箱与验证码
        verify(emailSender, times(1)).send(
                eq(email),
                anyString(),
                org.mockito.ArgumentMatchers.argThat(body -> body.contains(codeInDb)));
    }

    @Test
    void requestReset_blankEmail_returns200AndDoesNothing() throws Exception {
        int rowsBefore = countResetCodes();
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(ForgotPasswordController.RESET_MESSAGE));
        assertEquals(rowsBefore, countResetCodes());
        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    // --- 辅助方法 ---

    private int countResetCodes() {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM \"PasswordResetCode\"", Integer.class);
        return c == null ? 0 : c;
    }

    /** 与 AuthIntegrationTest 边界测试相同的做法：直接写库播种数据，不绕过服务层。 */
    private void seedUserWithEmail(String username, String email) {
        jdbc.update("DELETE FROM \"User\" WHERE username = ?", username);
        jdbc.update("INSERT INTO \"User\" (username, passwordHash, email) VALUES (?, ?, ?)",
                username, UserRepository_TEST_BCRYPT_PLACEHOLDER, email);
    }

    private static final String UserRepository_TEST_BCRYPT_PLACEHOLDER =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    // --- 固定时钟（与 AuthIntegrationTest.TestClock 同构，可独立推进）---

    static class TestClock extends Clock {
        private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
        private final AtomicReference<Instant> instant = new AtomicReference<>(BASE_INSTANT);
        public void advance(Duration duration) { instant.updateAndGet(prev -> prev.plus(duration)); }
        public void reset() { instant.set(BASE_INSTANT); }
        Instant now() { return instant.get(); }
        @Override public Instant instant() { return instant.get(); }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean @Primary
        TestClock testClock() { return new TestClock(); }
    }
}
