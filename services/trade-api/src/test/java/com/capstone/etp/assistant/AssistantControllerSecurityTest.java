package com.capstone.etp.assistant;

import com.capstone.etp.config.SecurityConfig;
import com.capstone.etp.controller.ApiExceptionHandler;
import com.capstone.etp.controller.ExtensionExceptionHandler;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.security.JwtAuthenticationEntryPoint;
import com.capstone.etp.security.JwtAuthenticationFilter;
import com.capstone.etp.security.JwtTestFixture;
import com.capstone.etp.security.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real JWT filter and the real assistant service over the controller, with the data and the model stubbed. */
@WebMvcTest(controllers = AssistantController.class)
@Import({
        SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, JwtVerifier.class,
        AccountAuthorizer.class, ApiExceptionHandler.class, ExtensionExceptionHandler.class,
        AssistantService.class, AssistantProperties.class
})
@TestPropertySource(properties = {
        "app.auth.jwt.secret=test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345",
        "app.auth.jwt.algorithm=HS256",
        "app.auth.jwt.issuer=auth-service"
})
class AssistantControllerSecurityTest {

    private static final String SECRET = "test-secret-key-with-at-least-sixty-four-bytes-for-hs512-validation-12345";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AssistantDataService data;
    @MockBean
    private GeminiClient gemini;
    @MockBean
    private ChatRateLimiter limiter;

    @BeforeEach
    void setUp() {
        when(limiter.tryAcquire(any())).thenReturn(true);
        when(gemini.isConfigured()).thenReturn(false);
    }

    private static String token(long accountId) {
        return JwtTestFixture.token(SECRET, "auth-service", accountId, List.of("CUSTOMER"), Instant.now().minusSeconds(60), Instant.now().plusSeconds(900));
    }

    private static final String BODY = "{\"message\":\"Should I sell AAPL?\"}";

    @Test
    void noTokenIs401AndNothingIsRead() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/assistant/chat").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH-401"));
        mvc.perform(get("/api/v1/accounts/1/assistant/quick-actions")).andExpect(status().isUnauthorized());

        verifyNoInteractions(data, gemini);
    }

    @Test
    void anotherCustomersAccountIs403() throws Exception {
        mvc.perform(post("/api/v1/accounts/2/assistant/chat").header("Authorization", "Bearer " + token(1))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACC-403"));

        verify(data, never()).ground(any(), any(), any());
    }

    @Test
    void adviceComesBackAsAnAdvisoryBlockWithTheRedirect() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/assistant/chat").header("Authorization", "Bearer " + token(1))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advisoryBlocked").value(true))
                .andExpect(jsonPath("$.redirectTo").value("/advice"))
                .andExpect(jsonPath("$.intent").value("ADVISORY"))
                .andExpect(jsonPath("$.answer").value("I cannot provide investment advice. Please use the Advice page for research signals."));
    }

    @Test
    void aBlankOrOversizedMessageIsRefused() throws Exception {
        mvc.perform(post("/api/v1/accounts/1/assistant/chat").header("Authorization", "Bearer " + token(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  \"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/accounts/1/assistant/chat").header("Authorization", "Bearer " + token(1))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + "a".repeat(501) + "\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void quickActionsAreListedForTheOwner() throws Exception {
        mvc.perform(get("/api/v1/accounts/1/assistant/quick-actions").header("Authorization", "Bearer " + token(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[2].id").value("last_transactions"));
    }
}
