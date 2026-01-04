package com.ubaid.Auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET = "5367566B59703373367639792F423F4528482B4D6251655468576D5A71347437";

    private Key getSignKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    // UPDATED: Now accepts full user details
    public String generateAccessToken(String uid, String email, String role, String name, String photoUrl) {
        return Jwts.builder()
                .setSubject(email)
                .claim("uid", uid)           // <--- Store UID
                .claim("role", role)         // <--- Store Role
                .claim("name", name)         // <--- Store Name
                .claim("photo", photoUrl)    // <--- Store Photo
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60)) // 1 Hour
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken() {
        return java.util.UUID.randomUUID().toString();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // --- NEW Extractors for our UserEntity ---
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractUid(String token) {
        return extractClaim(token, claims -> claims.get("uid", String.class));
    }

    public String extractName(String token) {
        return extractClaim(token, claims -> claims.get("name", String.class));
    }

    public String extractPhoto(String token) {
        return extractClaim(token, claims -> claims.get("photo", String.class));
    }
    // -----------------------------------------

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSignKey()).build().parseClaimsJws(token).getBody();
    }
}