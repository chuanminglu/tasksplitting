package com.tasksplitting.api;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {
    /** Single session validity tier in this phase; "remember me" differentiation is T00104. */
    public static final int SESSION_VALIDITY_HOURS = 2;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock;

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public String login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username == null ? "" : username)
            .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "账号不存在"));
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.passwordHash())) {
            throw new AuthException("INVALID_PASSWORD", "密码错误");
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusHours(SESSION_VALIDITY_HOURS);
        sessionRepository.create(token, user.id(), expiresAt);
        return token;
    }
}
