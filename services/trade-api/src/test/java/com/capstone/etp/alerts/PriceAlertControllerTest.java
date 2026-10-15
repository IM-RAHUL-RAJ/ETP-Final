package com.capstone.etp.alerts;

import com.capstone.etp.config.JacksonConfig;
import com.capstone.etp.controller.ExtensionExceptionHandler;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PriceAlertControllerTest {

    private AlertService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(AlertService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AlertController(service))
                .setControllerAdvice(new ExtensionExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonConfig().objectMapper()))
                .setValidator(validator)
                .build();
    }

    private static AlertResponse alert(AlertOperator operator, boolean enabled) {
        return new AlertResponse(7L, "AAPL", operator, new BigDecimal("230.50"), enabled,
                AlertStatus.ACTIVE, DeliveryState.NONE, null, null, Instant.parse("2026-10-01T10:00:00Z"), 0);
    }

    @Test
    void postCreatesAnAlertWithTheOperatorAsASymbol() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(alert(AlertOperator.GTE, true));

        mvc.perform(post("/api/v1/accounts/1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":230.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.condition").value(">="))
                .andExpect(jsonPath("$.price").value(230.50))
                .andExpect(jsonPath("$.enabled").value(true));
        verify(service).create(1L, new CreateAlertRequest("AAPL", AlertOperator.GTE, new BigDecimal("230.50"), null));
    }

    @Test
    void everyOperatorIsAccepted() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(alert(AlertOperator.EQ, true));

        for (String symbol : List.of(">", ">=", "<", "<=", "==")) {
            mvc.perform(post("/api/v1/accounts/1/alerts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"symbol\":\"AAPL\",\"condition\":\"" + symbol + "\",\"price\":10}"))
                    .andExpect(status().isCreated());
        }
    }

    @Test
    void anUnknownOperatorOrAMissingPriceIsRefusedWith422() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"condition\":\"<>\",\"price\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        mvc.perform(post("/api/v1/accounts/1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"condition\":\">\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/accounts/1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"condition\":\">\",\"price\":0}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void putUpdatesTheCondition() throws Exception {
        when(service.update(eq(1L), eq(7L), any())).thenReturn(alert(AlertOperator.LT, true));

        mvc.perform(put("/api/v1/accounts/1/alerts/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"condition\":\"<\",\"price\":225}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("<"));
        verify(service).update(1L, 7L, new UpdateAlertRequest(null, AlertOperator.LT, new BigDecimal("225"), null));
    }

    @Test
    void putEnabledTogglesTheSwitchAndAnswersWithIt() throws Exception {
        when(service.setEnabled(1L, 7L, false)).thenReturn(alert(AlertOperator.GTE, false));

        mvc.perform(put("/api/v1/accounts/1/alerts/7/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void putEnabledWithoutAValueIsRefused() throws Exception {
        mvc.perform(put("/api/v1/accounts/1/alerts/7/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(service);
    }

    @Test
    void deleteRemovesTheAlert() throws Exception {
        when(service.remove(1L, 7L)).thenReturn(alert(AlertOperator.GTE, true));

        mvc.perform(delete("/api/v1/accounts/1/alerts/7")).andExpect(status().isOk());
        verify(service).remove(1L, 7L);
    }

    @Test
    void triggersReturnsTheHistory() throws Exception {
        when(service.triggers(1L, 7L)).thenReturn(new AlertTriggersResponse(7L, List.of(
                new AlertTriggersResponse.Trigger(3L, Instant.parse("2026-10-07T14:05:11Z"),
                        new BigDecimal("231.50"), "SENT", 456L))));

        mvc.perform(get("/api/v1/accounts/1/alerts/7/triggers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(7))
                .andExpect(jsonPath("$.triggers[0].notificationStatus").value("SENT"))
                .andExpect(jsonPath("$.triggers[0].triggerPrice").value(231.50));
    }

    @Test
    void anotherAccountIsRefusedWith403OnEveryEndpoint() throws Exception {
        when(service.list(5L)).thenThrow(new AccountAccessDeniedException());
        when(service.update(eq(5L), eq(7L), any())).thenThrow(new AccountAccessDeniedException());
        when(service.setEnabled(5L, 7L, true)).thenThrow(new AccountAccessDeniedException());
        when(service.remove(5L, 7L)).thenThrow(new AccountAccessDeniedException());
        when(service.triggers(5L, 7L)).thenThrow(new AccountAccessDeniedException());

        mvc.perform(get("/api/v1/accounts/5/alerts")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/accounts/5/alerts/7").contentType(MediaType.APPLICATION_JSON).content("{\"price\":5}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("ACC-403"));
        mvc.perform(put("/api/v1/accounts/5/alerts/7/enabled").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/accounts/5/alerts/7")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/5/alerts/7/triggers")).andExpect(status().isForbidden());
    }

    @Test
    void anUnknownAlertIs404AndAConflictKeepsItsOwnStatus() throws Exception {
        when(service.remove(1L, 99L)).thenThrow(new ModuleException(HttpStatus.NOT_FOUND, "ALR-404", "Alert not found"));
        when(service.setEnabled(1L, 8L, true)).thenThrow(new ModuleException(HttpStatus.CONFLICT, "ALR-409", "Handing over"));

        mvc.perform(delete("/api/v1/accounts/1/alerts/99"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("ALR-404"));
        mvc.perform(put("/api/v1/accounts/1/alerts/8/enabled").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isConflict());
    }
}
