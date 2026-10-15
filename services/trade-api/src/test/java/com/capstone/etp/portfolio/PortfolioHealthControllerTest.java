package com.capstone.etp.portfolio;

import com.capstone.etp.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalInt;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioHealthControllerTest {

    private RealisedPnlMapper database;
    private FauxnanceQuoteClient fauxnance;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        database = mock(RealisedPnlMapper.class);
        fauxnance = mock(FauxnanceQuoteClient.class);
        PortfolioHealthController controller = new PortfolioHealthController(database, fauxnance,
                Clock.fixed(Instant.parse("2026-10-19T14:05:11Z"), ZoneOffset.UTC), 300);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonConfig().objectMapper()))
                .build();
    }

    @Test
    void healthyReportsBothDependenciesAndTheRemainingQuota() throws Exception {
        when(database.ping()).thenReturn(1);
        when(fauxnance.usageRemaining()).thenReturn(OptionalInt.of(1873));

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.dependencies[0].name").value("postgres"))
                .andExpect(jsonPath("$.dependencies[0].status").value("ok"))
                .andExpect(jsonPath("$.dependencies[0].quotaRemaining").doesNotExist())
                .andExpect(jsonPath("$.dependencies[1].name").value("fauxnance"))
                .andExpect(jsonPath("$.dependencies[1].quotaRemaining").value(1873))
                .andExpect(jsonPath("$.asOf").value("2026-10-19T14:05:11Z"));
    }

    @Test
    void aFailingDependencyDegradesTheStatusButNotTheResponse() throws Exception {
        when(database.ping()).thenThrow(new IllegalStateException("db down"));
        when(fauxnance.usageRemaining()).thenThrow(new QuoteProvider.QuoteProviderException("down"));

        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("degraded"))
                .andExpect(jsonPath("$.dependencies[0].status").value("down"))
                .andExpect(jsonPath("$.dependencies[1].status").value("down"));
    }

    @Test
    void usageCostsQuotaSoItIsOnlyAskedForOncePerWindow() throws Exception {
        when(database.ping()).thenReturn(1);
        when(fauxnance.usageRemaining()).thenReturn(OptionalInt.of(10));

        mvc.perform(get("/health"));
        mvc.perform(get("/health"));
        mvc.perform(get("/health"));

        verify(fauxnance, times(1)).usageRemaining();
    }
}
