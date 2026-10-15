package com.capstone.etp.controller;

import com.capstone.etp.dto.ErrorResponse;
import com.capstone.etp.exception.ETPException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void handleUnauthorized_returns401AndAuthCode() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized();
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("AUTH-401", response.getBody().getErrorCode());
    }

    @Test
    void handleValidation_returns422AndValidationCode() {
        ResponseEntity<ErrorResponse> response = handler.handleValidation();
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VAL-422", response.getBody().getErrorCode());
    }

    @Test
    void handleCatalogue_mapsKnownAndDefaultCodes() {
        assertEquals(HttpStatus.NOT_FOUND.value(), statusFor("ACC-404"));
        assertEquals(HttpStatus.NOT_FOUND.value(), statusFor("INS-404"));
        assertEquals(HttpStatus.FORBIDDEN.value(), statusFor("ACC-403"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), statusFor("ORD-400"));
        assertEquals(HttpStatus.CONFLICT.value(), statusFor("ORD-409"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), statusFor("VAL-422"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), statusFor("CUR-422"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), statusFor("AUTH-401"));
        assertEquals(HttpStatus.BAD_REQUEST.value(), statusFor("UNKNOWN"));
    }

    private int statusFor(String code) {
        ETPException ex = new ETPException(code, "message") {};
        return handler.handleCatalogue(ex).getStatusCode().value();
    }
}
