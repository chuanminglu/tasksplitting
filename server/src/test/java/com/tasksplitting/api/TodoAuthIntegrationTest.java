package com.tasksplitting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that /api/todos requires a valid bearer token (AuthInterceptor)
 * and that the login endpoint remains accessible without one.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-todo-auth.db"
})
@AutoConfigureMockMvc
@Import(TodoAuthIntegrationTest.FixedClockConfiguration.class)
class TodoAuthIntegrationTest {
    private static final Instant BASE_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    /** Helper: log in and return the token. */
    private String loginAndGetToken(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("token").asText();
    }

    @Test
    void getTodosWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/todos"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void postTodosWithoutTokenReturns401() throws Exception {
        mvc.perform(post("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"secret\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTodosWithValidTokenReturns200() throws Exception {
        String username = "auth-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void postTodosWithValidTokenReturns201() throws Exception {
        String username = "auth2-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        MvcResult result = mvc.perform(post("/api/todos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"protected\"}"))
            .andExpect(status().isCreated())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(body.path("title").asText().isEmpty());
    }

    @Test
    void getTodosReturnsLatestCreatedAtFirst() throws Exception {
        String username = "order-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        createTodo(token, "A");
        // SQLite CURRENT_TIMESTAMP has second-level precision; the test clock does not
        // feed the Todo insert, so sleep long enough to guarantee a strictly later
        // second for the next row.
        Thread.sleep(1100);
        createTodo(token, "B");
        Thread.sleep(1100);
        createTodo(token, "C");

        MvcResult result = mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        // findAll is not user-scoped; other tests in this class may have inserted rows.
        // Assert only the relative order: C before B before A (descending createdAt).
        int idxC = findIndex(body, "C");
        int idxB = findIndex(body, "B");
        int idxA = findIndex(body, "A");
        assertTrue(idxC >= 0 && idxB >= 0 && idxA >= 0, "expected C, B, A all present");
        assertTrue(idxC < idxB && idxB < idxA,
            "expected descending createdAt order C->B->A, got C@" + idxC + " B@" + idxB + " A@" + idxA);
    }

    private void createTodo(String token, String title) throws Exception {
        mvc.perform(post("/api/todos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\"}"))
            .andExpect(status().isCreated());
    }

    private int findIndex(JsonNode body, String title) {
        for (int i = 0; i < body.size(); i++) {
            if (title.equals(body.get(i).path("title").asText())) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void getTodosWithExpiredTokenReturns401() throws Exception {
        String username = "auth3-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        // Expire the session by updating its expiresAt to the past
        jdbc.update("UPDATE \"Session\" SET expiresAt = ? WHERE token = ?",
            LocalDateTime.now(clock).minusHours(1).format(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), token);

        mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTodosWithExpiresAtExactlyNowStillReturns200() throws Exception {
        String username = "boundary-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));
        String token = loginAndGetToken(username);

        // Set expiresAt to exactly the current clock instant — isBefore is false, so valid.
        String nowStr = LocalDateTime.now(clock).format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        jdbc.update("UPDATE \"Session\" SET expiresAt = ? WHERE token = ?", nowStr, token);

        mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    void getTodosWithUnknownTokenReturns401() throws Exception {
        mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer nonexistent-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getTodosWithWrongSchemeReturns401() throws Exception {
        mvc.perform(get("/api/todos")
                .header("Authorization", "Basic dXNlcjpwYXNz"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }

    @Test
    void getTodosWithEmptyBearerReturns401() throws Exception {
        mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer "))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
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

        void reset() {
            current.set(Instant.parse("2026-01-01T00:00:00Z"));
        }

        void advance(Duration d) {
            current.updateAndGet(v -> v.plus(d));
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current.get(); }
        LocalDateTime now() { return LocalDateTime.ofInstant(instant(), getZone()); }
    }
}
