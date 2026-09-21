package com.tasksplitting.api;

public record LoginRequest(String username, String password, boolean rememberMe) {}
