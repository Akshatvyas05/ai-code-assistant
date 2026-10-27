package com.akshat.ai_code_assistant.controller;

import com.akshat.ai_code_assistant.repository.RefreshTokenRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode register(String email) throws Exception {
        String body = mapper.writeValueAsString(java.util.Map.of(
                "name", "Test User", "email", email, "password", "Passw0rd123"));
        String json = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void logout_invalidatesOldAccessTokenAndDeletesRefreshTokens() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String access = register(email).get("accessToken").asText();
        String bearer = "Bearer " + access;

        mockMvc.perform(get("/test/me").header("Authorization", bearer)).andExpect(status().isOk());

        mockMvc.perform(post("/auth/logout").header("Authorization", bearer)).andExpect(status().isNoContent());

        mockMvc.perform(get("/test/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        assertEquals(0, refreshTokenRepository.countByUserEmail(email));
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginAfterLogout_issuesWorkingToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String bearer = "Bearer " + register(email).get("accessToken").asText();
        mockMvc.perform(post("/auth/logout").header("Authorization", bearer)).andExpect(status().isNoContent());

        Thread.sleep(1100); // JWT iat has 1s resolution

        String body = mapper.writeValueAsString(java.util.Map.of("email", email, "password", "Passw0rd123"));
        String json = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fresh = "Bearer " + mapper.readTree(json).get("accessToken").asText();

        mockMvc.perform(get("/test/me").header("Authorization", fresh)).andExpect(status().isOk());
    }
}
