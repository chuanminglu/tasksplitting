package com.tasksplitting.api;

import java.time.LocalDateTime;

public record Todo(int id, String title, boolean completed, LocalDateTime createdAt) {}
