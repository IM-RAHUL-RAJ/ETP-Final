package com.capstone.etp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WatchlistEntry {
    private String symbol;
    private String name;
    private String exchange;
    private String assetClass;
    private String currency;
    private boolean tradable;
    private Instant addedAt;
}
