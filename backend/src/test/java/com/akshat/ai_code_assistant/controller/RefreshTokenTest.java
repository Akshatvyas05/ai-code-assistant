package com.akshat.ai_code_assistant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode register() throws Exception {
        String body = "{\"name\":\"Jane\",\"email\":\"%s@example.com\",\"password\":\"Passw0rd123\"}".formatted(UUID.randomUUID());
        String json = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    private ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"%s\"}".formatted(token)));
    }

    @Test
    void refresh_rotatesTokenAndIssuesUsableAccessToken() throws Exception {
        String oldRefresh = register().get("refreshToken").asText();

        String json = refresh(oldRefresh).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode rotated = mapper.readTree(json);

        assertNotEquals(oldRefresh, rotated.get("refreshToken").asText());
        mockMvc.perform(get("/test/me").header("Authorization", "Bearer " + rotated.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedToken_isRejectedAndRevokesTheFamily() throws Exception {
        String oldRefresh = register().get("refreshToken").asText();
        String newRefresh = mapper.readTree(refresh(oldRefresh).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        refresh(oldRefresh).andExpect(status().isUnauthorized());
        refresh(newRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownToken_returns401() throws Exception {
        refresh("does-not-exist").andExpect(status().isUnauthorized());
    }

    @Test
    void blankToken_returns400() throws Exception {
        refresh("").andExpect(status().isBadRequest());
    }

    @Test
    void refreshAfterLogout_returns401() throws Exception {
        JsonNode auth = register();
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + auth.get("accessToken").asText()))
                .andExpect(status().isNoContent());
        refresh(auth.get("refreshToken").asText()).andExpect(status().isUnauthorized());
    }
}
