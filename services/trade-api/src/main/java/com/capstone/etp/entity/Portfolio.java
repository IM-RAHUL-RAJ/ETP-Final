package com.capstone.etp.entity;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Portfolio {
    private Long id;
    private Long accountId;
    private String name;
    private BigDecimal currentValue;
    private Instant createdAt;
    private Instant updatedAt;
}