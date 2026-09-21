package com.tasksplitting.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the existing input validation of TodoController.create:
 * an empty or blank title is rejected with 400 and nothing is written.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:sqlite:target/test-todo-validation.db"
})
@AutoConfigureMockMvc
class TodoValidationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** Helper: log in and return the token. */
    private String loginAndGetToken(String username) throws Exception {
        users.create(username, encoder.encode("correct-password"));
        MvcResult result = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"correct-password\"}"))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("token").asText();
    }

    private int countTodos(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/todos")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.isArray());
        return body.size();
    }

    @Test
    void createTodoWithEmptyTitleReturns400AndDoesNotPersist() throws Exception {
        String token = loginAndGetToken("val-empty-" + System.nanoTime());
        int before = countTodos(token);

        MvcResult result = mvc.perform(post("/api/todos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("message").isTextual());
        assertEquals(before, countTodos(token));
    }

    @Test
    void createTodoWithBlankTitleReturns400AndDoesNotPersist() throws Exception {
        String token = loginAndGetToken("val-blank-" + System.nanoTime());
        int before = countTodos(token);

        MvcResult result = mvc.perform(post("/api/todos")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(body.path("message").isTextual());
        assertEquals(before, countTodos(token));
    }
}
