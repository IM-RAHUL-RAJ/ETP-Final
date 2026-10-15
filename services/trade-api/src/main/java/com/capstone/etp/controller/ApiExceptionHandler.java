package com.capstone.etp.controller;

import com.capstone.etp.dto.ErrorResponse;
import com.capstone.etp.exception.ETPException;
import com.capstone.etp.security.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.builder()
                        .errorCode("AUTH-401")
                        .message("Unauthorised")
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation() {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.builder()
                        .errorCode("VAL-422")
                        .message("Invalid input")
                        .build());
    }

    @ExceptionHandler(ETPException.class)
    public ResponseEntity<ErrorResponse> handleCatalogue(ETPException exception) {
        return ResponseEntity.status(resolveStatus(exception.getCatalogueCode()))
                .body(ErrorResponse.builder()
                        .errorCode(exception.getCatalogueCode())
                        .message(exception.getMessage())
                        .build());
    }

    private HttpStatus resolveStatus(String code) {
        return switch (code) {
            case "ACC-404", "INS-404" -> HttpStatus.NOT_FOUND;
            case "ACC-403" -> HttpStatus.FORBIDDEN;
            case "ORD-400" -> HttpStatus.BAD_REQUEST;
            case "ORD-409" -> HttpStatus.CONFLICT;
            case "VAL-422", "CUR-422" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "AUTH-401" -> HttpStatus.UNAUTHORIZED;
            case "REG-404" -> HttpStatus.NOT_FOUND;
            case "REG-409" -> HttpStatus.CONFLICT;
            case "REG-422" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "REG-502" -> HttpStatus.BAD_GATEWAY;
            case "REG-503" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }
}
