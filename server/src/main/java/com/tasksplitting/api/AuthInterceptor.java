package com.tasksplitting.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Rejects requests to protected routes (currently {@code /api/todos/**}) unless a
 * valid, unexpired bearer token is presented in the {@code Authorization} header.
 * <p>
 * This is the permanent auth gate for the workboard; it has no feature-flag
 * branch — once deployed, it is always active.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final SessionRepository sessionRepository;
    private final Clock clock;

    public AuthInterceptor(SessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String token = extractBearerToken(request);
        if (token == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"missing or malformed Authorization header\"}}");
            return false;
        }

        SessionRepository.StoredSession session = sessionRepository.findByToken(token).orElse(null);
        if (session == null || session.expiresAt().isBefore(LocalDateTime.now(clock))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid or expired session token\"}}");
            return false;
        }
        // T00301: expose the resolved user to protected controllers (e.g. AvatarController)
        request.setAttribute("userId", session.userId());
        return true;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null) return null;
        // Expect "Bearer <token>"
        String prefix = "Bearer ";
        if (!header.startsWith(prefix) || header.length() <= prefix.length()) return null;
        String token = header.substring(prefix.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
