package com.akshat.ai_code_assistant.controller;

import com.akshat.ai_code_assistant.dto.AuthResponse;
import com.akshat.ai_code_assistant.dto.LoginRequest;
import com.akshat.ai_code_assistant.dto.RefreshTokenRequest;
import com.akshat.ai_code_assistant.dto.RegisterRequest;
import com.akshat.ai_code_assistant.dto.UserResponse;
import com.akshat.ai_code_assistant.entity.RefreshToken;
import com.akshat.ai_code_assistant.entity.User;
import com.akshat.ai_code_assistant.repository.UserRepository;
import com.akshat.ai_code_assistant.service.JwtService;
import com.akshat.ai_code_assistant.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Register, log in, refresh and log out")
public class AuthenticationController {

    private final AuthService authService;

    public AuthenticationController(AuthService authService){
        this.authService = authService;
    }

    @Operation(summary = "Register a new user", description = "Creates an account and returns an access + refresh token. Email is trimmed and lowercased.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Invalid input","status":400,"detail":"Validation failed for one or more fields","errors":{"password":"Password must contain at least one letter and one digit"}}"""))),
            @ApiResponse(responseCode = "409", description = "Email already registered", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Email already in use","status":409,"detail":"Email is already in use"}""")))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> registerUser(@Valid @RequestBody RegisterRequest request){
        String email = normalizeEmail(request.email());
        if(userRepository.existsByEmail(email)){
            throw new EmailAlreadyExistsException("Email is already in use");
        }

        var hashedPassword = passwordEncoder.encode(request.password());

        User user = new User(request.name().trim(), email, hashedPassword);

        User savedUser = userRepository.save(user);

        UserResponse response = new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
        String token = jwtService.generateToken(savedUser.getEmail(), savedUser.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser);

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Rotate a refresh token", description = "Exchanges a valid refresh token for a new access + refresh token. The old refresh token is revoked; reusing it revokes all of the user's tokens.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "400", description = "Refresh token missing", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Invalid input","status":400,"detail":"Validation failed for one or more fields","errors":{"refreshToken":"Refresh token is required"}}"""))),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or reused refresh token", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Authentication Failed","status":401,"detail":"Invalid or expired refresh token"}""")))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request){
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "Log out everywhere", description = "Deletes all of the caller's refresh tokens and invalidates every access token issued so far.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token", content = @Content)
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication){
        authService.logout((String) authentication.getPrincipal());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Log in", description = "Verifies credentials and returns an access + refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Logged in"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Invalid input","status":400,"detail":"Validation failed for one or more fields","errors":{"email":"Invalid email format"}}"""))),
            @ApiResponse(responseCode = "401", description = "Wrong email or password", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(value = """
{"title":"Authentication Failed","status":401,"detail":"Invalid email or password"}""")))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElseThrow(()-> new InvalidCredentialException("Invalid email or password"));

        if(!passwordEncoder.matches(request.password(), user.getPassword())) throw new InvalidCredentialException("Invalid email or password");

        UserResponse response = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );
        String token = jwtService.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return ResponseEntity.ok(new AuthResponse(token, refreshToken.getToken(), response));
    }
}
