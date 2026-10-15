package com.capstone.etp.executor.service;

import com.capstone.etp.executor.entity.Account;
import com.capstone.etp.executor.entity.Instrument;
import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.entity.PortfolioHolding;
import com.capstone.etp.executor.enums.OrderSide;
import com.capstone.etp.executor.enums.OrderStatus;
import com.capstone.etp.executor.exception.DeadLetterException;
import com.capstone.etp.executor.exception.OptimisticLockRetryException;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import com.capstone.etp.executor.rule.FillDecision;
import com.capstone.etp.executor.util.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;


@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final ExecutorOrderMapper mapper;

    @Transactional
    public SettlementResult settle(Order order, FillDecision initialDecision, Instrument instrument) {
        Instant now = Instant.now();


        Account account = mapper.findAccountById(order.getAccountId());

        FillDecision finalDecision = initialDecision;

        if (initialDecision.isFill()) {
            if (!account.isActive()) {
                // Account was suspended or closed after the order was accepted.
                finalDecision = FillDecision.reject("ACCOUNT_NOT_ACTIVE");
            } else if (order.getSide() == OrderSide.BUY) {
                BigDecimal requiredInInstrumentCurrency = order.getLimitPrice()
                        .multiply(BigDecimal.valueOf(order.getQuantity()));
                BigDecimal requiredInAccountCurrency = convertAmount(
                        requiredInInstrumentCurrency,
                        instrument.getCurrency(),
                        account.getCurrency());
                if (!account.canAfford(requiredInAccountCurrency)) {
                    finalDecision = FillDecision.reject("INSUFFICIENT_FUNDS");
                }
            } else {
                // Rule 7 re-check: held quantity at execution time.
                PortfolioHolding holding = mapper.findHolding(order.getAccountId(), order.getInstrumentId());
                int heldQty = holding != null ? holding.getQuantity() : 0;
                if (heldQty < order.getQuantity()) {
                    finalDecision = FillDecision.reject("INSUFFICIENT_HOLDINGS");
                }
            }
        }

        OrderStatus finalStatus = finalDecision.isFill() ? OrderStatus.FILLED : OrderStatus.REJECTED;

        // --- Guarded state transition -----------------------------------------
        // Returns 0 when a prior delivery already settled this order.
        int updated = mapper.settleOrderStatus(order.getId(), finalStatus, now);
        if (updated == 0) {
            log.info("Order {} already settled by a prior delivery — skipping duplicate (accountId={})",
                    order.getId(), order.getAccountId());
            return SettlementResult.alreadyHandled(order.getId());
        }

        // --- Apply writes based on final decision --------

        if (finalDecision.isFill()) {
            return applyFill(order, account, instrument, finalDecision.getExecutedPrice(), now);
        } else {
            return applyReject(order, account, instrument, finalDecision.getReason(), now);
        }
    }

    
    private SettlementResult applyFill(Order order, Account account,
                                       Instrument instrument, BigDecimal executedPrice,
                                       Instant now) {
        // Load current holding before writes so we can compute positionQuantityAfter
        PortfolioHolding currentHolding = mapper.findHolding(order.getAccountId(), order.getInstrumentId());
        int existingQty      = currentHolding != null ? currentHolding.getQuantity() : 0;
        BigDecimal existingAvg = currentHolding != null ? currentHolding.getAvgPrice() : BigDecimal.ZERO;

        BigDecimal limitPriceInAccountCurrency = convertAmount(
                order.getLimitPrice(),
                instrument.getCurrency(),
                account.getCurrency());
        BigDecimal executedPriceInAccountCurrency = convertAmount(
                executedPrice,
                instrument.getCurrency(),
                account.getCurrency());

        // Cash delta
        BigDecimal tradeValueInInstrumentCurrency = executedPrice.multiply(BigDecimal.valueOf(order.getQuantity()));
        BigDecimal tradeValueInAccountCurrency = convertAmount(
                tradeValueInInstrumentCurrency,
                instrument.getCurrency(),
                account.getCurrency());
        BigDecimal cashDelta  = order.getSide() == OrderSide.BUY
                ? tradeValueInAccountCurrency.negate()
                : tradeValueInAccountCurrency;
        BigDecimal newBalance = account.getCashBalance().add(cashDelta);

        // Log balance pre-change to make replay demos and audits straightforward
        log.info("Account {} balance before {} — applying cash delta {} => proposed balance {}",
                account.getId(), account.getCashBalance(), cashDelta, newBalance);

        // Optimistic-locked cash update
        int rows = mapper.updateAccountCashAndVersion(account.getId(), newBalance, account.getVersion());
        if (rows == 0) {
            // Another write bumped the version; roll back everything and retry.
            throw new OptimisticLockRetryException(
                    "Account " + account.getId() + " version conflict — will retry");
        }

        // Log successful cash update (still inside the DB transaction)
        log.info("Account {} cash updated to {} (applied by order {})",
                account.getId(), newBalance, order.getId());

        // Holdings
        Long portfolioId = mapper.findPortfolioIdByAccountId(account.getId());

        int posQtyAfter;
        BigDecimal avgCostAfter;

        if (order.getSide() == OrderSide.BUY) {
            posQtyAfter = existingQty + order.getQuantity();
            avgCostAfter = existingQty == 0
                    ? executedPrice
                    : existingAvg.multiply(BigDecimal.valueOf(existingQty))
                                 .add(executedPrice.multiply(BigDecimal.valueOf(order.getQuantity())))
                                 .divide(BigDecimal.valueOf(posQtyAfter), 2, RoundingMode.HALF_UP);
            mapper.upsertHolding(portfolioId, order.getInstrumentId(),
                    order.getQuantity(), executedPrice);
        } else {
            posQtyAfter  = existingQty - order.getQuantity();
            avgCostAfter = existingAvg; // SELL does not change average cost
            mapper.decreaseHolding(account.getId(), order.getInstrumentId(), order.getQuantity());
        }

        // Transaction record
        mapper.insertTransaction(order.getId(), order.getQuantity(),
                executedPriceInAccountCurrency, "EXECUTED", null, now);

        log.info("Order {} FILLED at {} — cash delta {}, position after {}",
                order.getId(), executedPrice, cashDelta, posQtyAfter);

        return new SettlementResult(
                order.getId(), order.getAccountId(), instrument.getSymbol(),
                order.getSide().name(), order.getQuantity(), limitPriceInAccountCurrency,
                executedPriceInAccountCurrency, OrderStatus.FILLED, null,
                cashDelta, posQtyAfter, avgCostAfter, now, false);
    }

    private SettlementResult applyReject(Order order, Account account,
                                         Instrument instrument, String reason, Instant now) {
        BigDecimal attemptedPriceInAccountCurrency = convertAmount(
                order.getLimitPrice(),
                instrument.getCurrency(),
                account.getCurrency());
        BigDecimal rejectedExecutedPrice = new BigDecimal("0.00");

        // Rejected orders carry 0.00 executed price by business rule.
        mapper.insertTransaction(order.getId(), order.getQuantity(),
                rejectedExecutedPrice, "FAILED", reason, now);

        // Current holding for positionQuantityAfter / averageCostAfter in the event
        PortfolioHolding holding = mapper.findHolding(order.getAccountId(), order.getInstrumentId());
        int posQtyAfter    = holding != null ? holding.getQuantity() : 0;
        BigDecimal avgCost = holding != null ? holding.getAvgPrice()  : BigDecimal.ZERO;

        log.info("Order {} REJECTED — reason: {} — account {} balance remains {}",
                order.getId(), reason, account.getId(), account.getCashBalance());

        return new SettlementResult(
                order.getId(), order.getAccountId(), instrument.getSymbol(),
                order.getSide().name(), order.getQuantity(), attemptedPriceInAccountCurrency,
                rejectedExecutedPrice, OrderStatus.REJECTED, reason,
                BigDecimal.ZERO, posQtyAfter, avgCost, now, false);
    }

    private BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        return CurrencyConverter.convert(fromCurrency, toCurrency, amount);
    }
}
