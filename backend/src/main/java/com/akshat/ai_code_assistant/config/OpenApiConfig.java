package com.akshat.ai_code_assistant.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "AI Code Assistant API",
        version = "v1",
        description = "Authentication and (upcoming) code review endpoints. Errors use RFC 7807 problem details."))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Access token from /auth/login, /auth/register or /auth/refresh")
public class OpenApiConfig {
}
