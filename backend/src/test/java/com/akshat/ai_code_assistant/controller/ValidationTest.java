package com.akshat.ai_code_assistant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ValidationTest {

    @Autowired
    MockMvc mockMvc;

    private ResultActions register(String name, String email, String password) throws Exception {
        String body = """
                {"name":"%s","email":"%s","password":"%s"}""".formatted(name, email, password);
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @Test
    void badEmail_isRejectedWithFieldError() throws Exception {
        for (String bad : new String[]{"not-an-email", "a@b", "a b@example.com"}) {
            register("Jane", bad, "Passw0rd123")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").value("Invalid email format"));
        }
    }

    @Test
    void weakPasswords_areRejected() throws Exception {
        for (String weak : new String[]{"short1", "onlyletters", "12345678", "a".repeat(70) + "1" + "bb"}) {
            register("Jane", uniqueEmail(), weak)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Test
    void blankFields_reportEachFieldInSameShape() throws Exception {
        register("", "", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid input"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void duplicateEmail_returns409ProblemDetail_evenWithDifferentCase() throws Exception {
        String email = uniqueEmail();
        register("Jane", email, "Passw0rd123").andExpect(status().isCreated());
        register("Jane", email.toUpperCase(), "Passw0rd123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already in use"));
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        String email = uniqueEmail();
        register("Jane", email, "Passw0rd123").andExpect(status().isCreated());
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Passw0rd123\"}".formatted(email.toUpperCase())))
                .andExpect(status().isOk());
    }

    @Test
    void malformedJson_returns400ProblemDetail() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid input"));
    }
}
