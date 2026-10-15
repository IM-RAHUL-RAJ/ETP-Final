package com.capstone.etp.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class JwtTestFixture {

    private JwtTestFixture() {
    }

    public static String token(
            String secret,
            String issuer,
            Long accountId,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("accountId", accountId)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public static String tokenWithHs512(
            String secret,
            String issuer,
            Long accountId,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("accountId", accountId)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public static String tokenWithAccountIds(
            String secret,
            String issuer,
            Long accountId,
            List<Long> accountIds,
            List<String> roles,
            Instant issuedAt,
            Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .claim("accountId", accountId)
                .claim("accountIds", accountIds)
                .claim("roles", roles)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
