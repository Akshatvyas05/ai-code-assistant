package com.akshat.ai_code_assistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/me")
    public ResponseEntity<?> getAuthenticatedUser(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Successfully accessed protected endpoint!",
                "authenticatedEmail", authentication.getPrincipal()
        ));
    }
}