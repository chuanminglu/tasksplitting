package com.tasksplitting.api;

import java.time.LocalDateTime;

public record User(int id, String username, String passwordHash, String email, LocalDateTime createdAt,
				   int failedLoginAttempts, LocalDateTime lockedUntil, String avatarUrl) {}
