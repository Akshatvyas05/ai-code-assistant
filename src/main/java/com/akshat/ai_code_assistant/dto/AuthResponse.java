package com.akshat.ai_code_assistant.dto;

public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {

    public AuthResponse(String token, UserResponse user){
        this(token, "Bearer", user);
    }
}
