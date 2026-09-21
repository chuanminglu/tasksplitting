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
    public static final int REMEMBER_ME_VALIDITY_DAYS = 7;
    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock;

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository, Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public String login(String username, String rawPassword, boolean rememberMe) {
        User user = userRepository.findByUsername(username == null ? "" : username)
            .orElseThrow(() -> new AuthException("USER_NOT_FOUND", "账号不存在"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            throw new AuthException("ACCOUNT_LOCKED", "账号已锁定", user.lockedUntil());
        }
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.passwordHash())) {
            int failedAttempts = user.failedLoginAttempts() + 1;
            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                LocalDateTime lockedUntil = now.plusMinutes(LOCKOUT_MINUTES);
                userRepository.updateLoginFailure(user.id(), failedAttempts, lockedUntil);
                throw new AuthException("ACCOUNT_LOCKED", "账号已锁定", lockedUntil);
            }
            userRepository.updateLoginFailure(user.id(), failedAttempts, null);
            throw new AuthException("INVALID_PASSWORD", "密码错误");
        }
        if (user.failedLoginAttempts() > 0 || user.lockedUntil() != null) {
            userRepository.resetLoginFailures(user.id());
        }
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = rememberMe
            ? now.plusDays(REMEMBER_ME_VALIDITY_DAYS)
            : now.plusHours(SESSION_VALIDITY_HOURS);
        sessionRepository.create(token, user.id(), expiresAt);
        return token;
    }
}
