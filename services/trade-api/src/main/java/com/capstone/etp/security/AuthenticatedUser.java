package com.capstone.etp.security;

import java.util.List;

public record AuthenticatedUser(
        String subject,
        List<Long> accountIds,
        List<String> roles) {
}
