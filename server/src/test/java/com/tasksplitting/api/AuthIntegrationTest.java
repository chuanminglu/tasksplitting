package com.tasksplitting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-login-enabled.db"
})
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.FixedClockConfiguration.class)
class AuthIntegrationTest {
    private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestClock clock;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    @Test
    void loginWithCorrectCredentialsReturnsTokenAndSessionRow() throws Exception {
        String username = "alice-" + System.nanoTime();
        users.create(username, new BCryptPasswordEncoder().encode("correct-password"));

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();

        Object token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token");
        assertNotNull(token);
        String tokenValue = ((com.fasterxml.jackson.databind.JsonNode) token).asText();
        assertFalse(tokenValue.isBlank());

        Timestamp expiresAt = jdbc.queryForObject(
            "SELECT expiresAt FROM \"Session\" WHERE token = ?", Timestamp.class, tokenValue);
        assertNotNull(expiresAt);
        LocalDateTime expected = clock.now().plusHours(2);
        assertTrue(Duration.between(expiresAt.toLocalDateTime(), expected).abs().toMinutes() <= 2,
            "expiresAt should be ~ issue time + 2h, got " + expiresAt + " expected ~ " + expected);
    }

    @Test
    void loginWithRememberMeStoresSevenDaySession() throws Exception {
        String username = createUser("remembered");

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\",\"rememberMe\":true}"))
            .andExpect(status().isOk())
            .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
        Timestamp expiresAt = jdbc.queryForObject(
            "SELECT expiresAt FROM \"Session\" WHERE token = ?", Timestamp.class, token);
        assertNotNull(expiresAt);
        LocalDateTime expected = clock.now().plusDays(AuthService.REMEMBER_ME_VALIDITY_DAYS);
        assertTrue(Duration.between(expiresAt.toLocalDateTime(), expected).abs().toMinutes() <= 2,
            "rememberMe expiresAt should be ~ issue time + 7d, got " + expiresAt + " expected ~ " + expected);
    }

    @Test
    void loginWithUnknownUsernameReturnsUserNotFoundCode() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing-" + System.nanoTime() + "\",\"password\":\"password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertEquals("USER_NOT_FOUND", objectMapper.readTree(result.getResponse().getContentAsString())
            .path("error").path("code").asText());
    }

    @Test
    void loginWithWrongPasswordReturnsInvalidPasswordCode() throws Exception {
        String username = "bob-" + System.nanoTime();
        users.create(username, new BCryptPasswordEncoder().encode("correct-password"));

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertEquals("INVALID_PASSWORD", objectMapper.readTree(result.getResponse().getContentAsString())
            .path("error").path("code").asText());
    }

    @Test
    void fifthWrongPasswordLocksAccountAndReturns423() throws Exception {
        String username = createUser("locked");

        for (int attempt = 1; attempt <= 4; attempt++) {
            postLogin(username, "wrong-password").andExpect(status().isUnauthorized());
        }

        MvcResult result = postLogin(username, "wrong-password")
            .andExpect(status().isLocked())
            .andReturn();
        assertLockedResponse(result, clock.now().plusMinutes(AuthService.LOCKOUT_MINUTES));
        assertEquals(5, failedAttempts(username));
    }

    @Test
    void lockedAccountRejectsLaterRequestsWithoutChangingLockTime() throws Exception {
        String username = createUser("locked-again");
        for (int attempt = 1; attempt <= 5; attempt++) {
            postLogin(username, "wrong-password");
        }
        Timestamp firstLockedUntil = lockedUntil(username);

        MvcResult result = postLogin(username, "correct-password")
            .andExpect(status().isLocked())
            .andReturn();

        assertLockedResponse(result, firstLockedUntil.toLocalDateTime());
        assertEquals(firstLockedUntil, lockedUntil(username));
        assertEquals(5, failedAttempts(username));
    }

    @Test
    void correctPasswordAfterLockExpiresLogsInAndClearsFailures() throws Exception {
        String username = createUser("recovers");
        for (int attempt = 1; attempt <= 5; attempt++) {
            postLogin(username, "wrong-password");
        }
        clock.advance(Duration.ofMinutes(AuthService.LOCKOUT_MINUTES));

        postLogin(username, "correct-password")
            .andExpect(status().isOk());

        assertEquals(0, failedAttempts(username));
        assertEquals(null, lockedUntil(username));
    }

    @Test
    void successfulLoginResetsConsecutiveFailuresBeforeNextFailure() throws Exception {
        String username = createUser("resets");
        postLogin(username, "wrong-password").andExpect(status().isUnauthorized());
        postLogin(username, "wrong-password").andExpect(status().isUnauthorized());
        postLogin(username, "correct-password").andExpect(status().isOk());

        assertEquals(0, failedAttempts(username));
        postLogin(username, "wrong-password").andExpect(status().isUnauthorized());
        assertEquals(1, failedAttempts(username));
    }

    @Test
    void healthEndpointIsReachableWithoutAuthorization() throws Exception {
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk());
    }

    @Test
    void loginEndpointIsReachableWithoutAuthorization() throws Exception {
        String username = createUser("public-login");

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
        assertFalse(token.isBlank());
    }

    @Test
    void loginWithEmptyUsernameReturnsUserNotFoundNot500() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"correct-password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertEquals("USER_NOT_FOUND", objectMapper.readTree(result.getResponse().getContentAsString())
            .path("error").path("code").asText());
    }

    @Test
    void loginWithMissingUsernameFieldReturnsUserNotFoundNot500() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"correct-password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();

        assertEquals("USER_NOT_FOUND", objectMapper.readTree(result.getResponse().getContentAsString())
            .path("error").path("code").asText());
    }

    private String createUser(String prefix) {
        String username = prefix + "-" + System.nanoTime();
        users.create(username, new BCryptPasswordEncoder().encode("correct-password"));
        return username;
    }

    private org.springframework.test.web.servlet.ResultActions postLogin(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    private void assertLockedResponse(MvcResult result, LocalDateTime expectedLockedUntil) throws Exception {
        var body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("ACCOUNT_LOCKED", body.path("error").asText());
        assertEquals(expectedLockedUntil.toString(), body.path("lockedUntil").asText());
    }

    private int failedAttempts(String username) {
        return jdbc.queryForObject(
            "SELECT failedLoginAttempts FROM \"User\" WHERE username = ?", Integer.class, username);
    }

    private Timestamp lockedUntil(String username) {
        return jdbc.queryForObject(
            "SELECT lockedUntil FROM \"User\" WHERE username = ?", Timestamp.class, username);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {
        @Bean
        @Primary
        TestClock testClock() {
            return new TestClock(BASE_INSTANT);
        }
    }

    static class TestClock extends Clock {
        private final AtomicReference<Instant> current;

        TestClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        LocalDateTime now() {
            return LocalDateTime.ofInstant(instant(), getZone());
        }

        void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }

        void reset() {
            current.set(BASE_INSTANT);
        }
    }
}
