package com.capstone.etp.security;

import com.capstone.etp.config.SecurityConfig;
import com.capstone.etp.controller.AccountController;
import com.capstone.etp.controller.ApiExceptionHandler;
import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.enums.AccountStatus;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtVerifier.class,
        AccountService.class,
        AccountAuthorizer.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345",
        "app.auth.jwt.algorithm=HS256",
        "app.auth.jwt.issuer=auth-service"
})
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345";

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.boot.test.mock.mockito.MockBean
    private AccountMapper accountMapper;

    @BeforeEach
    void setup() {
        when(accountMapper.getAccountById(1L)).thenReturn(AccountResponse.builder()
                .id(1L)
                .accountId("ACC0000001")
                .holderName("Priya Menon")
                .cashBalance(BigDecimal.valueOf(1000))
                .status(AccountStatus.ACTIVE)
                .version(1)
                .lastUpdated(Instant.now())
                .build());
    }

    @Test
    void missingHeaderReturnsAuth401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void wrongSchemeReturnsAuth401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void expiredTokenReturnsAuth401() throws Exception {
        String token = JwtTestFixture.token(
                SECRET,
                "auth-service",
                1L,
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(3600),
                Instant.now().minusSeconds(1800));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void forgedSignatureReturnsAuth401() throws Exception {
        String token = JwtTestFixture.token(
                "different-signing-secret-1234567890123456",
                "auth-service",
                1L,
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void wrongAlgorithmReturnsAuth401() throws Exception {
        String token = JwtTestFixture.tokenWithHs512(
                SECRET,
                "auth-service",
                1L,
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"))
                .andExpect(jsonPath("$.message").value("Unauthorised"));
    }

    @Test
    void accountMismatchReturnsAcc403WithSuspendedMessage() throws Exception {
        String token = JwtTestFixture.token(
                SECRET,
                "auth-service",
                2L,
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").value("Sorry you dont have access to this account"));
    }

    @Test
    void matchingAccountTokenAllowsAccess() throws Exception {
        String token = JwtTestFixture.token(
                SECRET,
                "auth-service",
                1L,
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void emptyAccountIdsClaimFallsBackToSingleAccountId() throws Exception {
        String token = JwtTestFixture.tokenWithAccountIds(
                SECRET,
                "auth-service",
                1L,
                List.of(),
                List.of("CUSTOMER"),
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(900));

        mockMvc.perform(get("/api/v1/accounts/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
