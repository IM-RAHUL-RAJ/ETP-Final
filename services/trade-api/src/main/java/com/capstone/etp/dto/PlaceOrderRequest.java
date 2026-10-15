package com.capstone.etp.dto;

import com.capstone.etp.enums.OrderSide;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceOrderRequest {
        @NotNull(message = "Account ID is required")
        @Min(value = 1, message = "Account ID must be at least 1")
        private Long accountId;

        @NotBlank(message = "Symbol is required")
        @Size(min = 1, max = 20, message = "Symbol must contain between 1 and 20 characters")
        private String symbol;

        @NotNull(message = "Order side is required")
        private OrderSide side;

        @NotNull(message = "Quantity is required")
//        @Min(value = 1, message = "Quantity must be at least 1")
        private Integer quantity;

        @NotNull(message = "Price is required")
//        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Price must have at most 2 decimal places")
        private BigDecimal price;

        @NotNull(message = "Idempotency key is required")
        private UUID idempotencyKey;
}