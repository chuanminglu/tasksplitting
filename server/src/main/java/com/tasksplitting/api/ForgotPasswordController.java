package com.tasksplitting.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * T00201（AC-1）：发起找回密码流程。
 * 无论邮箱是否注册，一律返回 200 + 相同响应体（不泄漏邮箱注册状态）。
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class ForgotPasswordController {

    public static final String RESET_MESSAGE = "如果该邮箱已注册，验证码已发送";

    private final ForgotPasswordService forgotPasswordService;

    public ForgotPasswordController(ForgotPasswordService forgotPasswordService) {
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> requestForgotPassword(
            @RequestBody(required = false) ForgotPasswordRequest request) {
        if (request != null) {
            forgotPasswordService.requestReset(request.email());
        }
        return ResponseEntity.ok(Map.of("message", RESET_MESSAGE));
    }
}
