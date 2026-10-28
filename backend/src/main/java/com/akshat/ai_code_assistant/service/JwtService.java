package com.akshat.ai_code_assistant.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import com.akshat.ai_code_assistant.entity.Role;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs){
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if(keyBytes.length < MIN_SECRET_BYTES){
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    private SecretKey getSigningKey(){
        return signingKey;
    }

    public String generateToken(String email, Role role){
        return Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token){
        return getClaims(token).getSubject();
    }

    public Role extractRole(String token){
        String role = getClaims(token).get("role", String.class);
        return role == null ? Role.USER : Role.valueOf(role);
    }

    public Date extractIssuedAt(String token){
        return getClaims(token).getIssuedAt();
    }

    public boolean isTokenValid(String token){
        try {
            return !getClaims(token).getExpiration().before(new Date());
        } catch (Exception ex){
            return false;
        }
    }

    private Claims getClaims(String token){
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
