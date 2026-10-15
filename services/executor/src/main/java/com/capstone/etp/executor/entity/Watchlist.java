package com.capstone.etp.executor.entity;

import lombok.*;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Watchlist {
    private Long accountId;
    private Long instrumentId;
    private Instant addedAt;
}
