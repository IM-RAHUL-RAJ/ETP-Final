package com.capstone.etp.alerts;

import jakarta.validation.constraints.NotNull;

public record EnabledRequest(@NotNull Boolean enabled) {
}
