package com.capstone.etp.preferences;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreferencesControllerTest {

    private PreferencesService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PreferencesService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new PreferencesController(service))
                .setControllerAdvice(new ExtensionExceptionHandler())
                // the application's own (strict) mapper: unknown fields are refused
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new JacksonConfig().objectMapper()))
                .setValidator(validator)
                .build();
    }

    private static PreferencesResponse response() {
        return new PreferencesResponse(1L, 1L, AlertChannel.EMAIL, "j***@example.com", ChannelSource.STORED,
                List.of(new PreferencesResponse.ChannelOption(AlertChannel.EMAIL, true, "j***@example.com")), null);
    }

    @Test
    void getReturnsThePreferences() throws Exception {
        when(service.get(1L)).thenReturn(response());

        mvc.perform(get("/api/v1/accounts/1/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertChannel").value("EMAIL"))
                .andExpect(jsonPath("$.contactDetail").value("j***@example.com"));
    }

    @Test
    void anotherAccountIsRefusedWith403AndTheCatalogueEnvelope() throws Exception {
        when(service.get(5L)).thenThrow(new AccountAccessDeniedException());

        mvc.perform(get("/api/v1/accounts/5/preferences"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void aCustomerCannotSupplyADestination() throws Exception {
        // The brief's server-side-request-forgery case: there is no field for it, so the request is refused.
        mvc.perform(put("/api/v1/accounts/1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"defaultAccountId":1,"alertChannel":"EMAIL","webhookUrl":"http://169.254.169.254/latest"}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        verifyNoInteractions(service);
    }

    @Test
    void aChannelOutsideTheFixedSetIsRefused() throws Exception {
        mvc.perform(put("/api/v1/accounts/1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAccountId\":1,\"alertChannel\":\"WEBHOOK\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
        verifyNoInteractions(service);
    }

    @Test
    void missingFieldsAreRefused() throws Exception {
        mvc.perform(put("/api/v1/accounts/1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"alertChannel\":\"EMAIL\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VAL-422"));
    }

    @Test
    void aValidPutIsPassedToTheService() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(response());

        mvc.perform(put("/api/v1/accounts/1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAccountId\":1,\"alertChannel\":\"EMAIL\"}"))
                .andExpect(status().isOk());
        verify(service).update(1L, new UpdatePreferencesRequest(1L, AlertChannel.EMAIL));
    }

    @Test
    void aServiceRefusalKeepsItsOwnStatusAndCode() throws Exception {
        when(service.update(eq(1L), any()))
                .thenThrow(new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "No phone"));

        mvc.perform(put("/api/v1/accounts/1/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultAccountId\":1,\"alertChannel\":\"SMS\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No phone"));
    }
}