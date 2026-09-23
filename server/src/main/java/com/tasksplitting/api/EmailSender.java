package com.tasksplitting.api;

/**
 * 邮件发送抽象（T00201）。生产环境可替换为真实 SMTP 实现；
 * 当前默认实现为 {@link LoggingEmailSender}（仅记日志，不产生网络请求）。
 */
public interface EmailSender {
    void send(String toEmail, String subject, String body);
}
