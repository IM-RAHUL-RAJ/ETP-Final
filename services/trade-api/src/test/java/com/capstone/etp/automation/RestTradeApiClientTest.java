package com.capstone.etp.automation;

import com.capstone.etp.dto.PlaceOrderRequest;
import com.capstone.etp.enums.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The real HTTP client against a mocked Trade REST API: the endpoint, the credential and the request body. */
class RestTradeApiClientTest {

    private static final UUID KEY = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private MockRestServiceServer server;
    private RestTradeApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade-api.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestTradeApiClient(builder.build(), accountId -> "jwt-for-" + accountId);
    }

    private static PlaceOrderRequest request() {
        return PlaceOrderRequest.builder().accountId(7L).symbol("AAPL").side(OrderSide.BUY).quantity(100)
                .price(new BigDecimal("12.00")).idempotencyKey(KEY).build();
    }

    @Test
    void postsTheOrderToTheTradeApiWithTheBearerTokenAndTheRightBody() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-for-7"))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.accountId").value(7))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(100))
                .andExpect(jsonPath("$.price").value(12.00))
                .andExpect(jsonPath("$.idempotencyKey").value(KEY.toString()))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"orderId\":\"" + ORDER_ID + "\",\"status\":\"NEW\",\"message\":\"Order created successfully\"}"));

        TradeApiClient.SubmissionResult result = client.submit(request());

        server.verify();
        assertEquals(TradeApiClient.Status.SUBMITTED, result.status());
        assertEquals(ORDER_ID, result.orderId());
    }

    @Test
    void aRefusalFromThePlatformIsRejectedNotFailed() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errorCode\":\"VAL-422\",\"message\":\"Invalid input\"}"));

        TradeApiClient.SubmissionResult result = client.submit(request());

        assertEquals(TradeApiClient.Status.REJECTED, result.status());
        assertEquals("HTTP 422 VAL-422", result.detail());
        assertNull(result.orderId());
    }

    @Test
    void anAuthorisationRefusalIsRejected() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertEquals(TradeApiClient.Status.REJECTED, client.submit(request()).status());
    }

    @Test
    void aServerErrorIsAFailureBecauseTheOutcomeIsUnknown() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        TradeApiClient.SubmissionResult result = client.submit(request());

        assertEquals(TradeApiClient.Status.FAILED, result.status());
        assertEquals("HTTP 503", result.detail());
    }

    @Test
    void aTimeoutIsAFailureAndMakesExactlyOneRequest() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        TradeApiClient.SubmissionResult result = client.submit(request());

        server.verify();
        assertEquals(TradeApiClient.Status.FAILED, result.status());
    }

    @Test
    void aConnectionFailureIsAFailure() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders")).andRespond(withException(new IOException("refused")));

        assertEquals(TradeApiClient.Status.FAILED, client.submit(request()).status());
    }

    @Test
    void anEmptyAnswerIsNotTakenForSuccess() {
        server.expect(requestTo("http://trade-api.test/api/v1/orders")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertEquals(TradeApiClient.Status.FAILED, client.submit(request()).status());
    }

    @Test
    void withoutATokenNothingIsSent() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://trade-api.test");
        MockRestServiceServer silent = MockRestServiceServer.bindTo(builder).build();
        RestTradeApiClient noToken = new RestTradeApiClient(builder.build(), accountId -> "  ");

        TradeApiClient.SubmissionResult result = noToken.submit(request());

        silent.verify();
        assertEquals(TradeApiClient.Status.FAILED, result.status());
    }
}
