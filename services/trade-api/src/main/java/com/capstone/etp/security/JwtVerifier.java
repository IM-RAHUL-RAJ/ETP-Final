package com.capstone.etp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtVerifier {

    private final SecretKey signingKey;
    private final String expectedAlgorithm;
    private final String expectedIssuer;

    public JwtVerifier(
            @Value("${app.auth.jwt.secret}") String secret,
            @Value("${app.auth.jwt.algorithm:HS256}") String expectedAlgorithm,
            @Value("${app.auth.jwt.issuer:auth-service}") String expectedIssuer) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expectedAlgorithm = expectedAlgorithm;
        this.expectedIssuer = expectedIssuer;
    }

    public JwtClaims verify(String token) {
        Jws<Claims> jws = verifySignature(token);
        Claims claims = jws.getPayload();
        verifyExpiry(claims);
        verifyAlgorithm(jws);
        verifyIssuer(claims);
        return toClaims(claims);
    }

    private Jws<Claims> verifySignature(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .clockSkewSeconds(0)
                    .build()
                    .parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Unauthorised");
        } catch (JwtException | IllegalArgumentException e) {
            throw new UnauthorizedException("Unauthorised");
        }
    }

    private void verifyExpiry(Claims claims) {
        Date expiration = claims.getExpiration();
        if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
            throw new UnauthorizedException("Unauthorised");
        }
    }

    private void verifyAlgorithm(Jws<Claims> jws) {
        String algorithm = jws.getHeader().getAlgorithm();
        if (!expectedAlgorithm.equals(algorithm)) {
            throw new UnauthorizedException("Unauthorised");
        }
    }

    private void verifyIssuer(Claims claims) {
        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new UnauthorizedException("Unauthorised");
        }
    }

    private JwtClaims toClaims(Claims claims) {
        Object accountIdRaw = claims.get("accountId");
        if (!(accountIdRaw instanceof Number accountIdNumber)) {
            throw new UnauthorizedException("Unauthorised");
        }

        Object accountIdsRaw = claims.get("accountIds");
        List<Long> accountIds = List.of(accountIdNumber.longValue());
        if (accountIdsRaw instanceof List<?> rawAccountIds) {
            List<Long> normalizedAccountIds = rawAccountIds.stream()
                    .map(id -> id instanceof Number ? ((Number) id).longValue() : null)
                    .filter(id -> id != null)
                    .toList();
            if (!normalizedAccountIds.isEmpty()) {
                accountIds = normalizedAccountIds;
            }
        }

        Object rolesRaw = claims.get("roles");
        if (!(rolesRaw instanceof List<?> rawRoles) || rawRoles.isEmpty()) {
            throw new UnauthorizedException("Unauthorised");
        }

        List<String> roles = rawRoles.stream()
                .map(Object::toString)
                .toList();

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new UnauthorizedException("Unauthorised");
        }

        return new JwtClaims(subject, accountIdNumber.longValue(), accountIds, roles);
    }
}
