package com.capstone.etp.portfolio;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.controller.ExtensionExceptionHandler;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.ModuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The field names and error codes are fixed by contracts/portfolio-api.yaml. */
class PortfolioControllerTest {

    private static final Instant AS_OF = Instant.parse("2026-10-19T14:05:11Z");

    private PortfolioService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PortfolioService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PortfolioController(service))
                .setControllerAdvice(new ExtensionExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonConfig().objectMapper()))
                .build();
    }

    @Test
    void theSummaryHasExactlyTheContractsFields() throws Exception {
        when(service.summary(1L)).thenReturn(new PortfolioSummaryResponse(1L, "USD", new BigDecimal("24500.75"),
                new BigDecimal("65120.00"), new BigDecimal("61300.00"), new BigDecimal("3820.00"),
                new BigDecimal("6.23"), new BigDecimal("1145.50"), new BigDecimal("89620.75"), 3, false, AS_OF));

        mvc.perform(get("/api/v1/portfolio/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.cashBalance").value(24500.75))
                .andExpect(jsonPath("$.marketValue").value(65120.00))
                .andExpect(jsonPath("$.costBasis").value(61300.00))
                .andExpect(jsonPath("$.unrealisedPnl").value(3820.00))
                .andExpect(jsonPath("$.unrealisedPnlPercent").value(6.23))
                .andExpect(jsonPath("$.realisedPnl").value(1145.50))
                .andExpect(jsonPath("$.totalValue").value(89620.75))
                .andExpect(jsonPath("$.positionCount").value(3))
                .andExpect(jsonPath("$.partial").value(false))
                .andExpect(jsonPath("$.asOf").value("2026-10-19T14:05:11Z"))
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void anUnpricedPositionKeepsItsNullPriceFieldsAndIsStale() throws Exception {
        when(service.positions(1L, null)).thenReturn(List.of(new PricedPositionResponse(1L, "INFY.NS", 40,
                new BigDecimal("1580.25"), new BigDecimal("63210.00"), null, null, null, null, "INR", null, true)));

        mvc.perform(get("/api/v1/portfolio/1/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("INFY.NS"))
                .andExpect(jsonPath("$[0].lastPrice").value((Object) null))
                .andExpect(jsonPath("$[0].marketValue").value((Object) null))
                .andExpect(jsonPath("$[0].priceAsOf").value((Object) null))
                .andExpect(jsonPath("$[0].stale").value(true))
                .andExpect(jsonPath("$[0].length()").value(12));
    }

    @Test
    void theSymbolFilterIsPassedThrough() throws Exception {
        when(service.positions(1L, "AAPL")).thenReturn(List.of());

        mvc.perform(get("/api/v1/portfolio/1/positions").param("symbol", "AAPL")).andExpect(status().isOk());
        verify(service).positions(1L, "AAPL");
    }

    @Test
    void pnlOmitsBySymbolUnlessAskedAndCarriesTheBounds() throws Exception {
        when(service.pnl(1L, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-19"), false))
                .thenReturn(new PnlResponse(1L, "USD", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-10-19"),
                        new BigDecimal("1145.50"), new BigDecimal("3820.00"), new BigDecimal("4965.50"), null, AS_OF));

        mvc.perform(get("/api/v1/portfolio/1/pnl").param("from", "2026-09-01").param("to", "2026-10-19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.totalPnl").value(4965.50))
                .andExpect(jsonPath("$.bySymbol").doesNotExist());
    }

    @Test
    void pnlWithBySymbolReturnsTheBreakdown() throws Exception {
        when(service.pnl(1L, null, null, true)).thenReturn(new PnlResponse(1L, "USD", null, null,
                new BigDecimal("1145.50"), new BigDecimal("862.00"), new BigDecimal("2007.50"),
                List.of(new PnlResponse.SymbolPnl("AAPL", new BigDecimal("1145.50"), new BigDecimal("862.00"),
                        new BigDecimal("2007.50"))), AS_OF));

        mvc.perform(get("/api/v1/portfolio/1/pnl").param("bySymbol", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value((Object) null))
                .andExpect(jsonPath("$.bySymbol[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.bySymbol[0].totalPnl").value(2007.50));
    }

    @Test
    void aMalformedDateIsInvalidInput() throws Exception {
        mvc.perform(get("/api/v1/portfolio/1/pnl").param("from", "not-a-date"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void aReversedRangeIsInvalidInput() throws Exception {
        when(service.pnl(1L, LocalDate.parse("2026-10-19"), LocalDate.parse("2026-09-01"), false))
                .thenThrow(new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input"));

        mvc.perform(get("/api/v1/portfolio/1/pnl").param("from", "2026-10-19").param("to", "2026-09-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void anotherAccountIs403NotA404() throws Exception {
        when(service.summary(2L)).thenThrow(new AccountAccessDeniedException());

        mvc.perform(get("/api/v1/portfolio/2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));
    }

    @Test
    void anUnknownAccountIs404() throws Exception {
        when(service.summary(3L)).thenThrow(new AccountNotFoundException(3L));

        mvc.perform(get("/api/v1/portfolio/3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACC-404"));
    }

    @Test
    void pricingUnavailableIs503WithTheContractsCode() throws Exception {
        when(service.summary(1L)).thenThrow(new PricingUnavailableException());
        when(service.positions(1L, null)).thenThrow(new PricingUnavailableException());

        mvc.perform(get("/api/v1/portfolio/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MKT-503"))
                .andExpect(jsonPath("$.message").value("Pricing unavailable"));
        mvc.perform(get("/api/v1/portfolio/1/positions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("MKT-503"));
    }
}
