package com.tasksplitting.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final LoginEventRepository loginEventRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final Clock clock;

    public AuthService(UserRepository userRepository, SessionRepository sessionRepository,
                       LoginEventRepository loginEventRepository, Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.loginEventRepository = loginEventRepository;
        this.clock = clock;
    }

    public String login(String username, String rawPassword, boolean rememberMe) {
        String safeUsername = username == null ? "" : username;
        User user = userRepository.findByUsername(safeUsername)
            .orElseThrow(() -> {
                recordEvent("USER_NOT_FOUND", safeUsername);
                return new AuthException("USER_NOT_FOUND", "账号不存在");
            });
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(now)) {
            recordEvent("ACCOUNT_LOCKED", safeUsername);
            throw new AuthException("ACCOUNT_LOCKED", "账号已锁定", user.lockedUntil());
        }
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.passwordHash())) {
            int failedAttempts = user.failedLoginAttempts() + 1;
            if (failedAttempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
                LocalDateTime lockedUntil = now.plusMinutes(LOCKOUT_MINUTES);
                userRepository.updateLoginFailure(user.id(), failedAttempts, lockedUntil);
                recordEvent("ACCOUNT_LOCKED", safeUsername);
                throw new AuthException("ACCOUNT_LOCKED", "账号已锁定", lockedUntil);
            }
            userRepository.updateLoginFailure(user.id(), failedAttempts, null);
            recordEvent("INVALID_PASSWORD", safeUsername);
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
        recordEvent("LOGIN_SUCCESS", safeUsername);
        return token;
    }

    /** Record a login-event row; a DB failure is logged and swallowed so it never blocks auth. */
    private void recordEvent(String eventType, String username) {
        try {
            loginEventRepository.record(eventType, username);
        } catch (Exception e) {
            log.warn("login event recording failed for {}", eventType, e);
        }
    }
}
