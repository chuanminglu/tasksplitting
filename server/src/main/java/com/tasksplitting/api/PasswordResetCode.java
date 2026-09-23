package com.tasksplitting.api;

import java.time.LocalDateTime;

public record PasswordResetCode(int id, String email, String code, LocalDateTime expiresAt,
                                boolean used, LocalDateTime createdAt) {}
