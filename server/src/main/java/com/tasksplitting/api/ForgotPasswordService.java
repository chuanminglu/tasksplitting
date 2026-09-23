package com.tasksplitting.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
}
