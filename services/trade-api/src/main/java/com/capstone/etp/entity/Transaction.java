package com.capstone.etp.entity;

import com.capstone.etp.enums.TransactionStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    private Long id;
    private UUID orderId;
    private int quantity;
    private BigDecimal executedPrice;
    private TransactionStatus status;
    private Instant executedAt;
}