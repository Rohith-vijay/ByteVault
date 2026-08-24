package com.marketplace.auth.security;

import com.marketplace.auth.entity.AuthCredentials;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret:default_super_secure_jwt_secret_placeholder_minimum_32_chars_long}")
    private String secret;

    @Value("${app.jwt.expiration:900000}") // 15 mins
    private long jwtExpiration;

    private Key getSignInKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret too short. Minimum 32 characters required. " +
                "Generate one with: openssl rand -base64 64");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(AuthCredentials credentials) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(credentials.getUsername())
                .claim("id", credentials.getId().toString())
                .claim("role", credentials.getRole().name())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + jwtExpiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUsername(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (JwtException e) {
            log.warn("JWT parse failure: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    public boolean isTokenValid(String token, String username) {
        try {
            Claims claims = parseClaims(token);
            return username.equals(claims.getSubject())
                    && claims.getExpiration().after(new Date());
        } catch (ExpiredJwtException e) {
            return false;
        } catch (JwtException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
