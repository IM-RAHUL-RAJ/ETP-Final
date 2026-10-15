package com.capstone.etp.security;

import java.util.List;

public record JwtClaims(
        String subject,
        Long accountId,
        List<Long> accountIds,
        List<String> roles) {
}
