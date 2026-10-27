package com.akshat.ai_code_assistant.controller;

import com.akshat.ai_code_assistant.entity.Role;
import com.akshat.ai_code_assistant.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RoleBasedAccessTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtService jwtService;

    private String bearer(Role role) {
        return "Bearer " + jwtService.generateToken("someone@example.com", role);
    }

    @Test
    void adminEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_asUser_returns403() throws Exception {
        mockMvc.perform(get("/admin/ping").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_asAdmin_returns200() throws Exception {
        mockMvc.perform(get("/admin/ping").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_asUser_returns200() throws Exception {
        mockMvc.perform(get("/test/me").header("Authorization", bearer(Role.USER)))
                .andExpect(status().isOk());
    }
}
