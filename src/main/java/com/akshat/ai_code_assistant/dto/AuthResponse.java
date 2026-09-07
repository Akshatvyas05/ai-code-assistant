package com.akshat.ai_code_assistant.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, UserResponse user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}