package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** What was created. The login is created separately, by the auth service, which emails the password. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationResponse {
    private Long clientId;
    private String clientRef;
    private Long accountId;
    private String accountRef;
    private String username;
    private String email;
}
