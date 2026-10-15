package com.capstone.etp.entity;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioHolding {
    private Long id;
    private Long portfolioId;
    private Long instrumentId;
    private int quantity;
    private BigDecimal avgPrice;
    private Instant updatedAt;

    public boolean updateAveragePrice(
            int newQuantity,
            BigDecimal newPrice) {

        if (newQuantity <= 0) {
            throw new IllegalArgumentException(
                    "New quantity must be greater than zero");
        }
        if (newPrice == null ||
                newPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Price cannot be null or negative");
        }
        if (this.quantity == 0) {
            this.quantity = newQuantity;
            this.avgPrice = newPrice;
            return true;
        }

        BigDecimal oldValue = this.avgPrice
                .multiply(BigDecimal.valueOf(this.quantity));

        BigDecimal newValue = newPrice
                .multiply(BigDecimal.valueOf(newQuantity));

        int totalQuantity = this.quantity + newQuantity;

        this.avgPrice = oldValue
                .add(newValue)
                .divide(
                        BigDecimal.valueOf(totalQuantity),
                        2,
                        RoundingMode.HALF_UP);

        this.quantity = totalQuantity;
        return true;
    }
}