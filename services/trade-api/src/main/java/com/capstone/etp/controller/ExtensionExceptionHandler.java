package com.capstone.etp.controller;

import com.capstone.etp.dto.ErrorResponse;
import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ETPException;
import com.capstone.etp.exception.ModuleException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@Slf4j
@RestControllerAdvice(basePackages = {
        "com.capstone.etp.preferences",
        "com.capstone.etp.notifications",
        "com.capstone.etp.alerts",
        "com.capstone.etp.portfolio",
        "com.capstone.etp.funds",
        "com.capstone.etp.candles",
        "com.capstone.etp.advice",
        "com.capstone.etp.automation",
        "com.capstone.etp.assistant"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExtensionExceptionHandler {

    @ExceptionHandler(ModuleException.class)
    public ResponseEntity<ErrorResponse> handleModule(ModuleException ex) {
        return respond(ex.getStatus(), ex.getCatalogueCode(), ex.getMessage());
    }

    @ExceptionHandler(AccountAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccountAccessDeniedException ex) {
        // The authorizer has already logged who asked for what.
        return respond(HttpStatus.FORBIDDEN, ex.getCatalogueCode(), ex.getMessage());
    }

    @ExceptionHandler(ETPException.class)
    public ResponseEntity<ErrorResponse> handleCatalogue(ETPException ex) {
        HttpStatus status = switch (ex.getCatalogueCode()) {
            case "ACC-404", "INS-404" -> HttpStatus.NOT_FOUND;
            case "ACC-403" -> HttpStatus.FORBIDDEN;
            case "VAL-422" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "AUTH-401" -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.BAD_REQUEST;
        };
        return respond(status, ex.getCatalogueCode(), ex.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<ErrorResponse> handleInvalidInput(Exception ex) {
        log.debug("Rejected invalid input: {}", ex.getClass().getSimpleName());
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
    }

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder().errorCode(code).message(message).build());
    }
}