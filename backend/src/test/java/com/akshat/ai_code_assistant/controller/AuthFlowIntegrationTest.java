package com.akshat.ai_code_assistant.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Passw0rd123";

    @Value("${app.jwt.secret}")
    String serviceSecret;

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private ResultActions json(String path, String body) throws Exception {
        return mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + token));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    @Test
    void fullFlow_register_login_protected_refresh_logout() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        JsonNode registered = body(json("/auth/register",
                "{\"name\":\"Jane\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated()));
        String firstRefresh = registered.get("refreshToken").asText();

        JsonNode loggedIn = body(json("/auth/login",
                "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email)));
        me(loggedIn.get("accessToken").asText()).andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticatedEmail").value(email));

        JsonNode refreshed = body(json("/auth/refresh",
                "{\"refreshToken\":\"%s\"}".formatted(loggedIn.get("refreshToken").asText()))
                .andExpect(status().isOk()));
        String freshAccess = refreshed.get("accessToken").asText();
        me(freshAccess).andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + freshAccess))
                .andExpect(status().isNoContent());

        me(freshAccess).andExpect(status().isUnauthorized());
        json("/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(refreshed.get("refreshToken").asText()))
                .andExpect(status().isUnauthorized());
        json("/auth/refresh", "{\"refreshToken\":\"%s\"}".formatted(firstRefresh))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPasswordAndUnknownUser_returnSame401() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        json("/auth/register", "{\"name\":\"Jane\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated());

        json("/auth/login", "{\"email\":\"%s\",\"password\":\"WrongPass999\"}".formatted(email))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        json("/auth/login", "{\"email\":\"nobody-%s@example.com\",\"password\":\"%s\"}".formatted(UUID.randomUUID(), PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void protectedEndpoint_rejectsMissingGarbageTamperedAndExpiredTokens() throws Exception {
        mockMvc.perform(get("/test/me")).andExpect(status().isUnauthorized());
        me("not-a-jwt").andExpect(status().isUnauthorized());

        String email = UUID.randomUUID() + "@example.com";
        String valid = body(json("/auth/register",
                "{\"name\":\"Jane\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
                .andExpect(status().isCreated())).get("accessToken").asText();
        me(valid).andExpect(status().isOk());

        // tampered payload (signature no longer matches)
        String[] parts = valid.split("\\.");
        me(parts[0] + "." + parts[1] + "x." + parts[2]).andExpect(status().isUnauthorized());

        // signed with a different key
        me(token(email, "a-completely-different-secret-key-0123456789", 60_000)).andExpect(status().isUnauthorized());

        // correctly signed but already expired
        me(token(email, serviceSecret, -60_000)).andExpect(status().isUnauthorized());
    }

    private String token(String email, String secret, long ttlMs) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
