package com.khistory.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    // IMPORTANT: In production, this should be in Vault/Secrets Manager.
    // Ideally, match the secret used by @backend.
    @Value("${jwt.secret:defaultSecretKeyForDevelopmentPurposeOnlyYouShouldChangeThisToAMoreSecureKeyLengthMustBeEnough}")
    private String secret;

    private Key key;

    @PostConstruct
    public void init() {
        // Match Backend Logic: Treat secret as raw string, not Base64 encoded
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Assumes "nickname" is stored in claims
    public String extractNickname(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("nickname", String.class);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        // parserBuilder() might be missing in some runtime contexts or older libs if
        // not refreshed.
        // Fallback to older chaining if builder fails, or ensure import.
        // But 0.11.5 definitely has parserBuilder.
        // Let's try the standard way again, maybe just a sync issue.
        // If lint persists, I will switch to `Jwts.parser()`.
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
