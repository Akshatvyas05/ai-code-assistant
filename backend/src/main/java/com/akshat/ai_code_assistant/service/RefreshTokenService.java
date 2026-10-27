package com.akshat.ai_code_assistant.service;

import com.akshat.ai_code_assistant.dto.AuthResponse;
import com.akshat.ai_code_assistant.dto.UserResponse;
import com.akshat.ai_code_assistant.entity.RefreshToken;
import com.akshat.ai_code_assistant.entity.User;
import com.akshat.ai_code_assistant.exception.InvalidCredentialException;
import com.akshat.ai_code_assistant.repository.RefreshTokenRepository;
import com.akshat.ai_code_assistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    // 7 days expiration in milliseconds
    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshTokenDurationMs;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    /**
     * Issue a new refresh token for a user
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID().toString(),
                Instant.now().plusMillis(refreshTokenDurationMs),
                user
        );
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Logout: delete all of the user's refresh tokens and invalidate every access
     * token issued up to now.
     */
    @Transactional
    public void logout(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        user.setTokensValidAfter(Instant.now());
        userRepository.save(user);
    }

    /**
     * Rotate Refresh Token: Revoke current token, check reuse, issue new pair
     */
    // noRollbackFor: the reuse/expiry branches delete tokens and then throw; the deletes must commit
    @Transactional(noRollbackFor = InvalidCredentialException.class)
    public AuthResponse rotateRefreshToken(String requestToken) {
        RefreshToken token = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new InvalidCredentialException("Invalid or expired refresh token"));

        // 1. REUSE DETECTION: If token was already revoked, an attacker is reusing it!
        // Action: Revoke ALL tokens for this user for security.
        if (token.isRevoked()) {
            refreshTokenRepository.deleteAllByUser(token.getUser());
            throw new InvalidCredentialException("Security alert: Token reuse detected. Please log in again.");
        }

        // 2. EXPIRATION CHECK
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new InvalidCredentialException("Refresh token has expired. Please log in again.");
        }

        User user = token.getUser();

        // 3. ROTATE: Mark current refresh token as revoked
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // 4. ISSUE NEW TOKENS
        String newAccessToken = jwtService.generateToken(user.getEmail(), user.getRole());
        RefreshToken newRefreshToken = createRefreshToken(user);

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getCreatedAt()
        );

        return new AuthResponse(newAccessToken, newRefreshToken.getToken(), userResponse);
    }
}