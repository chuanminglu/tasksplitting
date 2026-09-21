package com.tasksplitting.api;

import java.time.LocalDateTime;

public record User(int id, String username, String passwordHash, LocalDateTime createdAt) {}
