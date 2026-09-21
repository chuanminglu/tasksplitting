package com.tasksplitting.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String token = authService.login(
            request == null ? null : request.username(),
            request == null ? null : request.password(),
            request != null && request.rememberMe());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, Object>> handleAuthException(AuthException exception) {
        if ("ACCOUNT_LOCKED".equals(exception.getCode())) {
            LocalDateTime lockedUntil = exception.getLockedUntil();
            return ResponseEntity.status(423).body(Map.of(
                "error", "ACCOUNT_LOCKED",
                "lockedUntil", lockedUntil.toString()));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
            "error", Map.of("code", exception.getCode(), "message", exception.getMessage())));
    }
}
