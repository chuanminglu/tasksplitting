package com.tasksplitting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.feature.login-auth-enabled=false",
    "spring.datasource.url=jdbc:sqlite:target/test-login-disabled.db"
})
@AutoConfigureMockMvc
class AuthDisabledIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void loginReturns404WhenFlagDisabled() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"u\",\"password\":\"p\"}"))
            .andExpect(status().isNotFound());
    }
}
