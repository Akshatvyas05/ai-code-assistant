package com.akshat.ai_code_assistant.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        LocalDateTime createdAt
) {
}
