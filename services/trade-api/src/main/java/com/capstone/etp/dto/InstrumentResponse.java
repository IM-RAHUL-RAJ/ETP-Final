package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InstrumentResponse {
    private String symbol;
    private String name;
    private String exchange;
    private String assetClass;
    private String currency;
    private boolean tradable;
}
