package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private String errorCode;           // ACC-404, ACC-403, INS-404, ORD-400, ORD-409, VAL-422, AUTH-401
    private String message;
}
