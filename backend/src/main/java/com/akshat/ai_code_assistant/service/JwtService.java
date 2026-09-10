package com.akshat.ai_code_assistant.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final String SECRET_KEY = "yZ0ZuTOeFOPWvNmiaV6vK0lNDveXNbMITSq3WVOoS7E";
    private static final long EXPIRATION_TIME = 120000;

    private SecretKey getSigningKey(){
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email,String role){

        java.util.Map<String,Object> extractClaim=new java.util.HashMap<>();
        extractClaim.put("role",role);
        return Jwts.builder()
                .setSubject(email)
                .setClaims(extractClaim)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token){
        return getClaims(token).getSubject();
    }

    public String extractRole(String token){
        return getClaims(token).get("role",String.class);
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
