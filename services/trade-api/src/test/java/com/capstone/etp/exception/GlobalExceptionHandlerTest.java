package com.capstone.etp.exception;

import com.capstone.etp.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    //AccountNotFoundException -> ACC-404
    @Test
    void handleAccountNotFound_MapsToACC404With404Status() {
        Long accountId = 1L;
        AccountNotFoundException exception = new AccountNotFoundException(accountId);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ACC-404");
        assertThat(response.getBody().getMessage()).isEqualTo("Account not found");
        assertThat(response.getBody().getMessage()).doesNotContain(accountId.toString());
    }

    //AccountNotActiveException -> ACC-403
    @Test
    void handleAccountNotActive_MapsToACC403With403Status() {
        Long accountId = 1L;
        String status = "SUSPENDED";
        AccountNotActiveException exception = new AccountNotActiveException(accountId, status);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleAccountNotActive(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ACC-403");
        assertThat(response.getBody().getMessage()).isEqualTo("Account not active");
        assertThat(response.getBody().getMessage()).doesNotContain(status);
    }

    //InstrumentNotFoundException -> INS-404
    @Test
    void handleInstrumentNotFound_MapsToINS404With404Status() {
        String symbol = "INFY";
        InstrumentNotFoundException exception = new InstrumentNotFoundException(
                symbol, InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL
        );
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInstrumentNotFound(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("INS-404");
        assertThat(response.getBody().getMessage()).isEqualTo("Instrument not found: UNKNOWN_SYMBOL");
        assertThat(response.getBody().getMessage()).doesNotContain(symbol);
    }

    //InsufficientFundsException -> ORD-400
    @Test
    void handleInsufficientFunds_MapsToORD400With400Status() {
        Long accountId = 1L;
        BigDecimal required = new BigDecimal("5000.00");
        BigDecimal available = new BigDecimal("2000.00");
        InsufficientFundsException exception = new InsufficientFundsException(
                accountId, required, available
        );
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInsufficientFunds(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ORD-400");
        assertThat(response.getBody().getMessage()).isEqualTo("Insufficient funds");
        assertThat(response.getBody().getMessage()).doesNotContain(required.toString());
        assertThat(response.getBody().getMessage()).doesNotContain(available.toString());
    }

    //DuplicateOrderException -> ORD-409
    @Test
    void handleDuplicateOrder_MapsToORD409With409Status() {
        UUID idempotencyKey = UUID.randomUUID();
        DuplicateOrderException exception = new DuplicateOrderException(idempotencyKey);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleDuplicateOrder(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ORD-409");
        assertThat(response.getBody().getMessage()).isEqualTo("Duplicate order");
        assertThat(response.getBody().getMessage()).doesNotContain(idempotencyKey.toString());
    }

    @Test
    void handleOptimisticLock_MapsToORD409With409Status() {
        OptimisticLockException exception = new OptimisticLockException(
                "Account version mismatch during order placement; concurrent modification detected");
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleOptimisticLock(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("ORD-409");
        assertThat(response.getBody().getMessage())
                .isEqualTo("Account version mismatch during order placement; concurrent modification detected");
    }

    //InvalidOrderValueException -> VAL-422
    @Test
    void handleInvalidOrderValue_MapsToVAL422With422Status() {
        String validationMessage = "Order quantity must be positive";
        InvalidOrderValueException exception = new InvalidOrderValueException(validationMessage);
        ResponseEntity<ErrorResponse> response = exceptionHandler.handleInvalidOrderValue(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("VAL-422");
        assertThat(response.getBody().getMessage()).isEqualTo(validationMessage);
    }

    //Clients can branch on error codes (404 contains ACC-404 and INS-404)
    @Test
    void errorResponse_AllowsClientBranchingOnCode() {
        AccountNotFoundException accException = new AccountNotFoundException(1L);
        InstrumentNotFoundException insException = new InstrumentNotFoundException(
                "INFY", InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL
        );
        ResponseEntity<ErrorResponse> accResponse = exceptionHandler.handleAccountNotFound(accException);
        ResponseEntity<ErrorResponse> insResponse = exceptionHandler.handleInstrumentNotFound(insException);

        assertThat(accResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(insResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(accResponse.getBody().getErrorCode()).isEqualTo("ACC-404");
        assertThat(insResponse.getBody().getErrorCode()).isEqualTo("INS-404");
    }

    //Error codes and HTTP status mappings are correct per contract
    @Test
    void allExceptionsMappedCorrectlyPerContract() {
        // ACC-404 → 404
        assertThat(exceptionHandler.handleAccountNotFound(
                new AccountNotFoundException(1L)
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ACC-403 → 403
        assertThat(exceptionHandler.handleAccountNotActive(
                new AccountNotActiveException(1L, "SUSPENDED")
        ).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // INS-404 → 404
        assertThat(exceptionHandler.handleInstrumentNotFound(
                new InstrumentNotFoundException("INFY", InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL)
        ).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ORD-400 → 400
        assertThat(exceptionHandler.handleInsufficientFunds(
                new InsufficientFundsException(1L, new BigDecimal("100"), new BigDecimal("50"))
        ).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // ORD-409 → 409
        assertThat(exceptionHandler.handleDuplicateOrder(
                new DuplicateOrderException(UUID.randomUUID())
        ).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        assertThat(exceptionHandler.handleOptimisticLock(
                new OptimisticLockException("Account version mismatch during order placement; concurrent modification detected")
        ).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // VAL-422 → 422
        assertThat(exceptionHandler.handleInvalidOrderValue(
                new InvalidOrderValueException("Invalid value")
        ).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

        @Test
        void handleIllegalState_MapsToOrd409With400Status() {
                ResponseEntity<ErrorResponse> response = exceptionHandler.handleIllegalState(
                                new IllegalOrderStateException("terminal order")
                );

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getErrorCode()).isEqualTo("ORD-409");
        }

        @Test
        void handleCurrencyMismatch_MapsToCur422With422Status() {
                CurrencyMismatchException exception = new CurrencyMismatchException("account USD vs symbol INR");
                ResponseEntity<ErrorResponse> response = exceptionHandler.handleCurrencyMismatch(exception);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getErrorCode()).isEqualTo("CUR-422");
                assertThat(response.getBody().getMessage()).contains("Currency mapping validation failed");
        }

        @Test
        void handleEtpException_DefaultsTo500() {
                ETPException exception = new ETPException("GEN-500", "Generic failure") {};
                ResponseEntity<ErrorResponse> response = exceptionHandler.handleETPException(exception);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                assertThat(response.getBody()).isNotNull();
                assertThat(response.getBody().getErrorCode()).isEqualTo("GEN-500");
                assertThat(response.getBody().getMessage()).isEqualTo("Generic failure");
        }
}
