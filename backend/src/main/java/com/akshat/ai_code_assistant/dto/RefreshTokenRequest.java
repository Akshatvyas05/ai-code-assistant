package com.akshat.ai_code_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Schema(example = "3f1c9e0a-6b0e-4a63-9a43-7a1d8f6c2b11")
        String refreshToken
) {}