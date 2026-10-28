package com.akshat.ai_code_assistant.service;

import com.akshat.ai_code_assistant.dto.AuthResponse;
import com.akshat.ai_code_assistant.dto.LoginRequest;
import com.akshat.ai_code_assistant.dto.RegisterRequest;
import com.akshat.ai_code_assistant.dto.UserResponse;
import com.akshat.ai_code_assistant.entity.RefreshToken;
import com.akshat.ai_code_assistant.entity.User;
import com.akshat.ai_code_assistant.exception.EmailAlreadyExistsException;
import com.akshat.ai_code_assistant.exception.InvalidCredentialException;
import com.akshat.ai_code_assistant.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates the auth use cases so controllers stay thin: password checks,
 * email normalization and issuing the access + refresh token pair.
 */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // Verified against when the email is unknown, so "no such user" costs the same
    // BCrypt work as "wrong password" and response time does not reveal which emails exist.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Email is already in use");
        }
        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));
        return issueTokens(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPassword() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialException(BAD_CREDENTIALS);
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(String refreshToken) {
        return issueTokens(refreshTokenService.rotateRefreshToken(refreshToken).getUser());
    }

    public void logout(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialException("Invalid access token"));
        refreshTokenService.logout(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken.getToken(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
