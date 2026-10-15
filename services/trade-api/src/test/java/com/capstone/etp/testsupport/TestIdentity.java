package com.capstone.etp.testsupport;

import com.capstone.etp.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Puts a verified-token identity into the security context, as JwtAuthenticationFilter would. */
public final class TestIdentity {

    private TestIdentity() {
    }

    public static void customer(Long... accountIds) {
        set(new AuthenticatedUser("customer-" + accountIds[0], List.of(accountIds), List.of("CUSTOMER")));
    }

    public static void admin(Long... accountIds) {
        set(new AuthenticatedUser("admin-1", List.of(accountIds), List.of("ADMIN")));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void set(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }
}