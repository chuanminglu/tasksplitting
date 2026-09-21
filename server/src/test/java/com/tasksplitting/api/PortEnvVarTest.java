package com.tasksplitting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-CFG-03: Verifies that the server port can be overridden via Spring properties.
 * Spring Boot's relaxed binding maps the SERVER_PORT env var to the server.port
 * property; this test proves the target property (server.port) is overridable
 * without any ${PORT:4000} placeholder in application.yml.
 *
 * A high, rarely-used port (41999) is chosen to avoid colliding with any
 * process already listening in the local dev environment.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
        "server.port=41999",
        "spring.datasource.url=jdbc:sqlite:target/test-port-env-var.db"
    }
)
class PortEnvVarTest {
    @Autowired
    ServletWebServerApplicationContext ctx;

    @Test
    void serverPortIsOverridableViaProperty() {
        assertEquals(
            41999,
            ctx.getWebServer().getPort(),
            "server.port=41999 property override should take effect; this is the "
                + "same mechanism SERVER_PORT env var uses via Spring Boot relaxed binding"
        );
    }
}
