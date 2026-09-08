package com.akshat.ai_code_assistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    @GetMapping("/dashboard")
    public ResponseEntity<String> getUserDashboard(){
        return ResponseEntity.ok("Welcome to your user dashboard.");
    }
}
