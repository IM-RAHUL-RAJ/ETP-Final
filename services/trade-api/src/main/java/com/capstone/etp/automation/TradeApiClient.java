package com.capstone.etp.automation;

import com.capstone.etp.dto.PlaceOrderRequest;

import java.util.UUID;

/** The only way automation places an order: the existing Trade REST API. One attempt per call, never retried here. */
public interface TradeApiClient {

    SubmissionResult submit(PlaceOrderRequest request);

    enum Status {
        SUBMITTED,
        REJECTED,
        FAILED
    }

    record SubmissionResult(Status status, UUID orderId, String detail) {

        public static SubmissionResult submitted(UUID orderId, String detail) {
            return new SubmissionResult(Status.SUBMITTED, orderId, detail);
        }

        public static SubmissionResult rejected(String detail) {
            return new SubmissionResult(Status.REJECTED, null, detail);
        }

        public static SubmissionResult failed(String detail) {
            return new SubmissionResult(Status.FAILED, null, detail);
        }
    }
}
