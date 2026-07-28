package com.akshat.ai_code_assistant.controller;

import com.akshat.ai_code_assistant.Exception.InvalidCredentialException;
import com.akshat.ai_code_assistant.dto.LoginRequest;
import com.akshat.ai_code_assistant.dto.RegisterRequest;
import com.akshat.ai_code_assistant.dto.UserResponse;
import com.akshat.ai_code_assistant.entity.User;
import com.akshat.ai_code_assistant.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationController(UserRepository userRepository, PasswordEncoder passwordEncoder){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request){
        if(userRepository.existsByEmail(request.email())){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error: Email is already in use");
        }

        var hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.name(), request.email(), hashedPassword);

        User savedUser = userRepository.save(user);

        UserResponse response = new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request){
        User user = userRepository.findByEmail(request.email()).orElseThrow(()-> new InvalidCredentialException("Invalid email or password"));

        if(!passwordEncoder.matches(request.password(), user.getPassword())) throw new InvalidCredentialException("Invalid email or password");

        UserResponse response = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}
