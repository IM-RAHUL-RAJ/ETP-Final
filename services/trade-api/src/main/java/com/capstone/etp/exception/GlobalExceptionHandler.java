package com.capstone.etp.exception;

import com.capstone.etp.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

     //AccountNotFoundException - returns ACC-404 with status 404.
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        log.warn("Account not found: accountId={}", ex.getAccountId());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    // AccountNotActiveException - Returns ACC-403 with status 403.
    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(AccountNotActiveException ex) {
        log.warn("Account not active: accountId={}, status={}", ex.getAccountRef(), ex.getCurrentStatus());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    // InstrumentNotFoundException - Returns INS-404 with status 404.
    @ExceptionHandler(InstrumentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleInstrumentNotFound(InstrumentNotFoundException ex) {
        log.warn("Instrument not found: symbol={}, reason={}", ex.getSymbol(), ex.getReason());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    // InsufficientFundsException - Returns ORD-400 with status 400.
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        log.warn("Insufficient funds: accountId={}, required={}, available={}",
                ex.getaccountId(), ex.getRequiredAmount(), ex.getAvailableBalance());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    // DuplicateOrderException - Returns ORD-409 with status 409.
    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateOrder(DuplicateOrderException ex) {
        log.warn("Duplicate order: idempotencyKey={}", ex.getIdempotencyKey());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    // InvalidOrderValueException - Returns VAL-422 with status 422.
    @ExceptionHandler(InvalidOrderValueException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOrderValue(InvalidOrderValueException ex) {
        log.warn("Invalid order value: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    @ExceptionHandler(IllegalOrderStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalOrderStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleCurrencyMismatch(CurrencyMismatchException ex) {
        log.warn("Currency mismatch: {}", ex.getDetail());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    // RegistrationException - carries its own status (409 duplicate, 422 invalid, 502/503 auth service).
    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<ErrorResponse> handleRegistration(RegistrationException ex) {
        log.warn("Registration refused: code={} message={}", ex.getCatalogueCode(), ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(ex.getStatus()).body(errorResponse);
    }

    //handler for any generic ETPException
    @ExceptionHandler(ETPException.class)
    public ResponseEntity<ErrorResponse> handleETPException(ETPException ex) {
        log.error("Unexpected domain exception: code={}", ex.getCatalogueCode(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .errorCode(ex.getCatalogueCode())
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
