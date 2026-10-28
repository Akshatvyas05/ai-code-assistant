package com.akshat.ai_code_assistant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "Invalid email format")
        @Size(max = 254, message = "Email must be at most 254 characters")
        @Schema(example = "jane@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(example = "Passw0rd123")
        String password
) {
}
