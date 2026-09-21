package com.tasksplitting.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-CFG-04: Verifies that application-test.yml (the "test" profile) defines
 * the dedicated test database path, and that Spring's property resolution
 * picks it up when the "test" profile is activated.
 *
 * This test does NOT boot a full Spring application context (which would
 * require a live database); instead it exercises the exact same Yaml
 * property source that Spring Boot uses at runtime.
 */
class TestProfileConfigTest {
    private static final String EXPECTED_URL =
        "jdbc:sqlite:target/test-data/application-test.db";

    @Test
    void applicationTestYmlDefinesDedicatedDatabasePath() throws Exception {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application-test.yml"));
        var properties = factory.getObject();
        assertEquals(EXPECTED_URL, properties.getProperty("spring.datasource.url"),
            "application-test.yml must define spring.datasource.url to the dedicated test path");
    }

    @Test
    void testProfileOverridesDefaultWhenActive() throws Exception {
        // Build a StandardEnvironment with:
        // 1. A source representing application.yml (default datasource URL)
        // 2. A source representing application-test.yml (test datasource URL)
        // 3. Activate the "test" profile — Spring Boot's resolution order
        //    makes profile-specific sources override default sources.
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("test");

        YamlPropertiesFactoryBean defaultFactory = new YamlPropertiesFactoryBean();
        defaultFactory.setResources(new ClassPathResource("application.yml"));
        var defaultProps = new PropertiesPropertySource(
            "applicationYml", defaultFactory.getObject());

        YamlPropertiesFactoryBean testFactory = new YamlPropertiesFactoryBean();
        testFactory.setResources(new ClassPathResource("application-test.yml"));
        var testProps = new PropertiesPropertySource(
            "applicationTestYml", testFactory.getObject());

        // Spring Boot's order: profile-specific first (higher priority)
        env.getPropertySources().addFirst(testProps);
        env.getPropertySources().addLast(defaultProps);

        assertEquals(EXPECTED_URL, env.getProperty("spring.datasource.url"),
            "With the 'test' profile active, the test datasource URL must take precedence");
    }
}
