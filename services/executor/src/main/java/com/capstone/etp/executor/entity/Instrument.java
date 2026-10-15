package com.capstone.etp.executor.entity;

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
public class Instrument {
    private Long id;
    private String symbol;
    private String currency;
    private boolean tradable;
}
