package com.capstone.etp.portfolio;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

// contracts/portfolio-api.yaml HealthResponse.
public record HealthResponse(String status, List<Dependency> dependencies, Instant asOf) {

    // {@code quotaRemaining} is for Fauxnance only and is null when it could not be read.
    public record Dependency(
            String name,
            String status,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer quotaRemaining) {
    }
}
