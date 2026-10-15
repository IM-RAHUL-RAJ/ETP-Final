package com.capstone.etp.alerts;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** "If the price of {symbol} is {condition} {price}". Enabled defaults to true. */
public record CreateAlertRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9.:^=\\-]+$") String symbol,
        @NotNull @JsonAlias("operator") AlertOperator condition,
        @NotNull @JsonAlias("threshold") @DecimalMin("0.0001") @Digits(integer = 14, fraction = 4) BigDecimal price,
        Boolean enabled) {

    boolean isEnabled() {
        return enabled == null || enabled;
    }
}
