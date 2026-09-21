package com.tasksplitting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-login-event.db"
})
@AutoConfigureMockMvc
class LoginEventIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private long countEvents(String username, String eventType) {
        Long c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM \"LoginEvent\" WHERE username = ? AND eventType = ?",
            Long.class, username, eventType);
        return c == null ? 0 : c;
    }

    @Test
    void loginSuccessWritesLoginSuccessEvent() throws Exception {
        String username = "evt-success-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk());

        assertEquals(1, countEvents(username, "LOGIN_SUCCESS"));
    }

    @Test
    void userNotFoundWritesUserNotFoundEvent() throws Exception {
        String username = "evt-missing-" + System.nanoTime();

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
            .andExpect(status().isUnauthorized());

        assertEquals(1, countEvents(username, "USER_NOT_FOUND"));
    }

    @Test
    void invalidPasswordWritesInvalidPasswordEvent() throws Exception {
        String username = "evt-invalid-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
            .andExpect(status().isUnauthorized());

        assertEquals(1, countEvents(username, "INVALID_PASSWORD"));
    }

    @Test
    void fifthInvalidPasswordWritesAccountLockedEvent() throws Exception {
        String username = "evt-locked-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));

        for (int i = 0; i < 4; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
            .andExpect(status().is(423));

        assertEquals(1, countEvents(username, "ACCOUNT_LOCKED"));
    }

    @Test
    void lockedAccountSecondAttemptWritesSecondAccountLockedEvent() throws Exception {
        String username = "evt-locked2-" + System.nanoTime();
        users.create(username, encoder.encode("correct-password"));

        // Attempts 1-4 return 401; the 5th triggers lockout (423); the 6th is rejected while locked (423).
        for (int i = 0; i < 6; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(i < 4 ? status().isUnauthorized() : status().is(423));
        }

        assertEquals(2, countEvents(username, "ACCOUNT_LOCKED"),
            "5th (trigger) and 6th (while locked) attempts should each record ACCOUNT_LOCKED");
    }
}
