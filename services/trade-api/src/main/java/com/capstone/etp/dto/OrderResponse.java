package com.capstone.etp.dto;

import com.capstone.etp.enums.OrderSide;
import com.capstone.etp.enums.OrderStatus;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private UUID orderId;
    private OrderStatus status;
    private String message;
    private String symbol;
    private OrderSide side;
    private Integer quantity;
    private BigDecimal price;
}