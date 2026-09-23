package com.tasksplitting.api;

public record ResetPasswordRequest(String email, String code, String newPassword) {}
