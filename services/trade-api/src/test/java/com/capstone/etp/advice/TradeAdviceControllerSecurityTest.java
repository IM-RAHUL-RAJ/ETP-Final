package com.capstone.etp.advice;

import com.capstone.etp.config.SecurityConfig;
import com.capstone.etp.controller.ApiExceptionHandler;
import com.capstone.etp.controller.ExtensionExceptionHandler;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.security.JwtAuthenticationEntryPoint;
import com.capstone.etp.security.JwtAuthenticationFilter;
import com.capstone.etp.security.JwtTestFixture;
import com.capstone.etp.security.JwtVerifier;
import com.capstone.etp.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real JWT filter and the real advice service, with the data behind them stubbed. */
@WebMvcTest(controllers = TradeAdviceController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtAuthenticationEntryPoint.class,
        JwtVerifier.class,
        AccountAuthorizer.class,
        ApiExceptionHandler.class,
        ExtensionExceptionHandler.class,
        TradeAdviceService.class,
        MovingAverageAdviceStrategy.class,
        TradeAdviceControllerSecurityTest.Fixed.class
})
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345",
        "app.auth.jwt.algorithm=HS256",
        "app.auth.jwt.issuer=auth-service",
        "app.advice.short-period=3",
        "app.advice.long-period=6"
})
class TradeAdviceControllerSecurityTest {

    private static final String SECRET = "test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345";

    @TestConfiguration
    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AccountService accounts;

    @MockBean
    private PriceHistoryProvider history;

    @BeforeEach
    void setUp() {
        when(accounts.getPositions(1L)).thenReturn(List.of(
                PositionResponse.builder().accountId(1L).symbol("AAPL").quantity(5).averageCost(BigDecimal.TEN).build()));
        when(accounts.getWatchlist(1L)).thenReturn(List.of());
        when(history.points("AAPL", 90)).thenReturn(
                PricePoint.undated(List.of(8, 9, 10, 14, 15, 16).stream().map(BigDecimal::valueOf).toList()));
    }

    private static String token(long accountId) {
        return JwtTestFixture.token(SECRET, "auth-service", accountId, List.of("CUSTOMER"),
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(900));
    }

    @Test
    void noTokenIsRefusedWith401AndNothingIsRead() throws Exception {
        mvc.perform(get("/api/v1/trade-advice"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));

        verifyNoInteractions(accounts, history);
    }

    @Test
    void anInvalidTokenIsRefusedWith401() throws Exception {
        mvc.perform(get("/api/v1/trade-advice").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/trade-advice/AAPL").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedSignatureIsRefusedWith401() throws Exception {
        String forged = JwtTestFixture.token("different-signing-secret-1234567890123456", "auth-service", 1L,
                List.of("CUSTOMER"), Instant.now().minusSeconds(60), Instant.now().plusSeconds(900));

        mvc.perform(get("/api/v1/trade-advice").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCustomerGetsAdviceForTheirOwnHoldings() throws Exception {
        mvc.perform(get("/api/v1/trade-advice").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].signal").value("BUY"))
                .andExpect(jsonPath("$[0].reasoning[0]").isNotEmpty())
                .andExpect(jsonPath("$[0].timestamp").value("2026-10-07T12:00:00Z"))
                .andExpect(jsonPath("$[0].length()").value(6))
                .andExpect(jsonPath("$[0].indicators.trend").value("BULLISH"))
                .andExpect(jsonPath("$[0].chart.length()").value(6));
    }

    @Test
    void oneInstrumentCanBeAskedForByName() throws Exception {
        mvc.perform(get("/api/v1/trade-advice/aapl").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.signal").value("BUY"));
    }

    @Test
    void askingForAnotherCustomersAccountIsRefusedWith403() throws Exception {
        mvc.perform(get("/api/v1/trade-advice").param("accountId", "2").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        org.mockito.Mockito.verify(accounts, org.mockito.Mockito.never()).getPositions(2L);
    }

    @Test
    void anInstrumentTheCustomerDoesNotHoldOrWatchIsA404() throws Exception {
        mvc.perform(get("/api/v1/trade-advice/TSLA").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ADV-404"));
    }
}
