package com.akshat.ai_code_assistant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/status")
    public ResponseEntity<String> getAdminStatus(){
        return ResponseEntity.ok("Welcome admin! The system is fully operational.");
    }
}
