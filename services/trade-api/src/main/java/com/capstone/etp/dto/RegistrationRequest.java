package com.capstone.etp.dto;

import com.capstone.etp.enums.AccountType;
import com.capstone.etp.enums.Gender;
import com.capstone.etp.enums.RiskProfile;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What an admin enters to open a client and an account. The fields mirror the clients and accounts tables. */
@Getter
@Setter
@NoArgsConstructor
public class RegistrationRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    /** The part before the @ becomes the username. */
    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @Pattern(regexp = "^[0-9]{10}$")
    private String phone;

    @Past
    private LocalDate dateOfBirth;

    private Gender gender;

    @NotNull
    private AccountType accountType;

    @NotBlank
    @Pattern(regexp = "^[A-Z]{3}$")
    private String currency;

    /** Opening cash. Defaults to zero. The accounts table caps it at 99,999,999.99. */
    @DecimalMin("0.00")
    @DecimalMax("99999999.99")
    private BigDecimal openingBalance;

    /** When given, an account_preferences row is created. */
    private RiskProfile riskProfile;
}
