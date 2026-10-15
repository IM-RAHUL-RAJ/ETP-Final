package com.capstone.etp.alerts;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Every field is optional: what is present changes, what is absent stays. */
public record UpdateAlertRequest(
        @Size(max = 20) @Pattern(regexp = "^[A-Za-z0-9.:^=\\-]+$") String symbol,
        @JsonAlias("operator") AlertOperator condition,
        @JsonAlias("threshold") @DecimalMin("0.0001") @Digits(integer = 14, fraction = 4) BigDecimal price,
        Boolean enabled) {
}
