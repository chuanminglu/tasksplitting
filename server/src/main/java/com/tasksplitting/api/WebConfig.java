package com.tasksplitting.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link AuthInterceptor} on the workboard routes so that
 * {@code /api/todos/**} requires a valid bearer session token.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // T00301: the avatar upload endpoint (POST /api/avatars) also requires a valid
        // bearer session. GET /api/avatars/** stays public — it only serves already-
        // uploaded bytes and the UUID-based filename is not guessable.
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/todos/**", "/api/avatars");
    }
}
