package com.capstone.etp.automation;

import com.capstone.etp.dto.ErrorResponse;
import com.capstone.etp.dto.OrderResponse;
import com.capstone.etp.dto.PlaceOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * POST /api/v1/orders on the Trade REST API, with a token for the order's own account as the bearer token. Exactly one request
 * per call: a failure is reported, not retried, so an uncertain outcome can never become a second order.
 */
@Slf4j
public class RestTradeApiClient implements TradeApiClient {

    static final String ORDERS_PATH = "/api/v1/orders";

    private final RestClient restClient;
    private final TokenSource tokens;

    public RestTradeApiClient(RestClient restClient, TokenSource tokens) {
        this.restClient = restClient;
        this.tokens = tokens;
    }

    @Override
    public SubmissionResult submit(PlaceOrderRequest request) {
        String token;
        try {
            token = tokens.tokenFor(request.getAccountId());
        } catch (RuntimeException e) {
            return SubmissionResult.failed("no token could be made for the account");
        }
        if (token == null || token.isBlank()) {
            return SubmissionResult.failed("no token could be made for the account");
        }
        try {
            OrderResponse response = restClient.post()
                    .uri(ORDERS_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OrderResponse.class);
            if (response == null || response.getOrderId() == null) {
                return SubmissionResult.failed("the Trade API answered without an order id");
            }
            return SubmissionResult.submitted(response.getOrderId(), "status " + response.getStatus());
        } catch (RestClientResponseException e) {
            String detail = "HTTP " + e.getStatusCode().value() + describe(e);
            // A 4xx is the platform saying no. Anything else is a failure whose outcome we do not know.
            return e.getStatusCode().is4xxClientError() ? SubmissionResult.rejected(detail) : SubmissionResult.failed(detail);
        } catch (RestClientException e) {
            // Timeouts and connection failures. The message can carry the URL; the type is enough.
            return SubmissionResult.failed(e.getClass().getSimpleName() + (e.getCause() == null ? "" : ": " + e.getCause().getClass().getSimpleName()));
        }
    }

    private static String describe(RestClientResponseException e) {
        try {
            ErrorResponse body = e.getResponseBodyAs(ErrorResponse.class);
            return body == null || body.getErrorCode() == null ? "" : " " + body.getErrorCode();
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
