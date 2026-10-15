package com.capstone.etp.automation;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Signs the short-lived token automation presents to the Trade REST API, so the customer does not have to hand
 * over a login token that would expire. The token names one account and the CUSTOMER role, lasts a minute, and is
 * only ever made for an account whose owner has switched a strategy on. The Trade API verifies it like any other.
 */
@Component
public class AutomationTokenIssuer implements TokenSource {

    static final Duration LIFETIME = Duration.ofSeconds(60);

    private final SecretKey key;
    private final String issuer;
    private final Clock clock;

    public AutomationTokenIssuer(@Value("${app.auth.jwt.secret}") String secret,
                                 @Value("${app.auth.jwt.issuer:auth-service}") String issuer,
                                 Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.clock = clock;
    }

    @Override
    public String tokenFor(Long accountId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject("automation")
                .issuer(issuer)
                .claim("accountId", accountId)
                .claim("accountIds", List.of(accountId))
                .claim("roles", List.of("CUSTOMER"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(LIFETIME)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
