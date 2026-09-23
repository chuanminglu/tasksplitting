package com.tasksplitting.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * T00201（AC-1）：发起找回密码流程。
 * 邮箱已注册 → 生成 6 位数字验证码（10 分钟有效）入库并经 EmailSender 发送；
 * 邮箱未注册 → 不做任何动作（不抛异常、不发邮件、不创建记录）。
 * 验证码只出现在日志与数据库中，绝不进入 HTTP 响应体。
 */
@Service
public class ForgotPasswordService {
    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);
    /** 验证码有效期：10 分钟 */
    public static final int CODE_VALIDITY_MINUTES = 10;
    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final UserRepository userRepository;
    private final PasswordResetCodeRepository codeRepository;
    private final EmailSender emailSender;
    private final Clock clock;

    public ForgotPasswordService(UserRepository userRepository,
                                 PasswordResetCodeRepository codeRepository,
                                 EmailSender emailSender,
                                 Clock clock) {
        this.userRepository = userRepository;
        this.codeRepository = codeRepository;
        this.emailSender = emailSender;
        this.clock = clock;
    }

    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim();
        if (userRepository.findByEmail(normalized).isEmpty()) {
            log.info("forgot-password requested for unregistered email, no action taken");
            return;
        }
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        LocalDateTime expiresAt = LocalDateTime.now(clock).plusMinutes(CODE_VALIDITY_MINUTES);
        codeRepository.insert(normalized, code, expiresAt);
        emailSender.send(normalized,
            "您的找回密码验证码",
            "验证码：" + code + "，" + CODE_VALIDITY_MINUTES + " 分钟内有效。");
    }

    /**
     * T00202（AC-2）：验证码校验 + 设置新密码。
     * 验证码不存在 / 已使用（used=true）/ 已过期（expiresAt 早于当前时刻；恰好等于不算过期）
     * → 抛 {@link AuthException}（code = INVALID_OR_EXPIRED_CODE）。
     * 校验通过 → 用 BCrypt 哈希新密码更新 User.passwordHash，并标记该验证码 used=true。
     */
    public void resetPassword(String email, String code, String newPassword) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            throw new AuthException("INVALID_OR_EXPIRED_CODE", "验证码无效或已过期");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new AuthException("INVALID_OR_EXPIRED_CODE", "新密码不能为空");
        }
        String normalizedEmail = email.trim();
        String normalizedCode = code.trim();
        PasswordResetCode record = codeRepository.findByEmailAndCode(normalizedEmail, normalizedCode)
            .orElseThrow(() -> new AuthException("INVALID_OR_EXPIRED_CODE", "验证码无效或已过期"));
        if (record.used()) {
            throw new AuthException("INVALID_OR_EXPIRED_CODE", "验证码已使用");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (record.expiresAt() == null || record.expiresAt().isBefore(now)) {
            throw new AuthException("INVALID_OR_EXPIRED_CODE", "验证码已过期");
        }
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new AuthException("INVALID_OR_EXPIRED_CODE", "验证码无效或已过期"));
        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePasswordHash(user.id(), newHash);
        codeRepository.markUsed(record.id());
        log.info("password reset completed for email={}", normalizedEmail);
    }
}
