package com.tasksplitting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Verifies the "埋点失败不阻塞主流程" DoD: when LoginEventRepository.record throws,
 * the login endpoint still returns its normal response.
 *
 * Runs in its own Spring context (separate @SpringBootTest) so the mocked
 * repository does not leak into the other event tests.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-login-event-resilience.db"
})
@AutoConfigureMockMvc
class LoginEventResilienceTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;
    @MockBean LoginEventRepository loginEventRepository;

    @Test
    void loginStillSucceedsWhenEventRecordingThrows() throws Exception {
        String username = "evt-resilient-" + System.nanoTime();
        users.create(username, new BCryptPasswordEncoder().encode("correct-password"));
        doThrow(new DataAccessException("simulated db failure") {}).
            when(loginEventRepository).record(org.mockito.ArgumentMatchers.anyString(),
                                             org.mockito.ArgumentMatchers.anyString());

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(body.path("token").asText().isBlank(),
            "login must still return a token when event recording fails");
    }

    @Test
    void loginFailureStillReturnsErrorWhenEventRecordingThrows() throws Exception {
        String username = "evt-resilient-nf-" + System.nanoTime();
        doThrow(new DataAccessException("simulated db failure") {}).
            when(loginEventRepository).record(org.mockito.ArgumentMatchers.anyString(),
                                             org.mockito.ArgumentMatchers.anyString());

        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        org.junit.jupiter.api.Assertions.assertEquals("USER_NOT_FOUND",
            body.path("error").path("code").asText(),
            "USER_NOT_FOUND error body must still be returned when event recording fails");
    }
}
