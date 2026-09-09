package com.akshat.ai_code_assistant.controller;

import com.akshat.ai_code_assistant.exception.InvalidCredentialException;
import com.akshat.ai_code_assistant.dto.AuthResponse;
import com.akshat.ai_code_assistant.dto.LoginRequest;
import com.akshat.ai_code_assistant.dto.RegisterRequest;
import com.akshat.ai_code_assistant.dto.UserResponse;
import com.akshat.ai_code_assistant.entity.RefreshToken;
import com.akshat.ai_code_assistant.entity.User;
import com.akshat.ai_code_assistant.repository.UserRepository;
import com.akshat.ai_code_assistant.service.JwtService;
import com.akshat.ai_code_assistant.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthenticationController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, RefreshTokenService refreshTokenService){
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request){
        if(userRepository.existsByEmail(request.email())){
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Error: Email is already in use");
        }

        var hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.name(), request.email(), hashedPassword);

        user.setRole("USER");

        User savedUser = userRepository.save(user);

        UserResponse response = new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                user.getRole(), savedUser.getCreatedAt()
        );
        String token = jwtService.generateToken(savedUser.getEmail(),savedUser.getRole());
        String token = jwtService.generateToken(savedUser.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthResponse(token, refreshToken.getToken(), response));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        User user = userRepository.findByEmail(request.email()).orElseThrow(()-> new InvalidCredentialException("Invalid email or password"));

        if(!passwordEncoder.matches(request.password(), user.getPassword())) throw new InvalidCredentialException("Invalid email or password");

        UserResponse response = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
        String token = jwtService.generateToken(user.getEmail(),user.getRole());
        String token = jwtService.generateToken(user.getEmail());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return ResponseEntity.ok(new AuthResponse(token, refreshToken.getToken(), response));
    }

}
