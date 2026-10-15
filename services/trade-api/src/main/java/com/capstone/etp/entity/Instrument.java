package com.capstone.etp.entity;

import com.capstone.etp.enums.AssetClass;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Instrument {

    private Long id;
    private String symbol;
    private String exchange;
    private String name;
    private AssetClass assetClass;
    private String currency;
    private boolean tradable;
    private Instant stoppedAt;
}