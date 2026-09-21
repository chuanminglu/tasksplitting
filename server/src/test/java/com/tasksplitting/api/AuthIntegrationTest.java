package com.tasksplitting.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.feature.login-auth-enabled=true",
    "spring.datasource.url=jdbc:sqlite:target/test-login-enabled.db"
})
@AutoConfigureMockMvc
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

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
        LocalDateTime expected = LocalDateTime.now().plusHours(2);
        assertTrue(Duration.between(expiresAt.toLocalDateTime(), expected).abs().toMinutes() <= 2,
            "expiresAt should be ~ issue time + 2h, got " + expiresAt + " expected ~ " + expected);
    }
}
