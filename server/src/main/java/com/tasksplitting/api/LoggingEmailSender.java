package com.tasksplitting.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * T00201：日志版邮件发送实现。仅将收件邮箱、主题、正文写入日志，
 * 不产生任何网络请求；测试通过 @MockBean 替换本 bean 来验证调用参数。
 */
@Component
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("[EMAIL] to={}, subject={}, body={}", toEmail, subject, body);
    }
}
