package com.akshat.ai_code_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(example = "3f1c9e0a-6b0e-4a63-9a43-7a1d8f6c2b11")
        String refreshToken,
        @Schema(example = "Bearer")
        String tokenType,
        UserResponse user
) {
    public AuthResponse(String accessToken, String refreshToken, UserResponse user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}