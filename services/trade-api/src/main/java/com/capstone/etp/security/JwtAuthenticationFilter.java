package com.capstone.etp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private final JwtVerifier jwtVerifier;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtVerifier jwtVerifier,
            JwtAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtVerifier = jwtVerifier;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || header.isBlank()) {
                throw new UnauthorizedException("Unauthorised");
            }

            if (!header.toLowerCase(Locale.ROOT).startsWith(BEARER_PREFIX.toLowerCase(Locale.ROOT))) {
                throw new UnauthorizedException("Unauthorised");
            }

            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (token.isBlank()) {
                throw new UnauthorizedException("Unauthorised");
            }

            JwtClaims claims = jwtVerifier.verify(token);
            AuthenticatedUser user = new AuthenticatedUser(claims.subject(), claims.accountIds(), claims.roles());
            var authorities = claims.roles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, ex);
        }
    }
}
