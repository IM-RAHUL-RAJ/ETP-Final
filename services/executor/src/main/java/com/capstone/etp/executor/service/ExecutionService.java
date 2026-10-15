package com.capstone.etp.executor.service;

import com.capstone.etp.executor.client.FauxnanceClient;
import com.capstone.etp.executor.entity.Instrument;
import com.capstone.etp.executor.entity.Order;
import com.capstone.etp.executor.enums.OrderStatus;
import com.capstone.etp.executor.exception.DeadLetterException;
import com.capstone.etp.executor.exception.OptimisticLockRetryException;
import com.capstone.etp.executor.exception.PriceUnavailableException;
import com.capstone.etp.executor.kafka.models.OrderPlacedPayload;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import com.capstone.etp.executor.rule.FillDecision;
import com.capstone.etp.executor.rule.FillRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.stereotype.Service;


import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutorOrderMapper mapper;
    private final FauxnanceClient fauxnanceClient;
    private final SettlementService settlementService;

    @Value("${app.executor.optimistic-lock.max-attempts:3}")
    private int maxLockAttempts;

    public SettlementResult execute(OrderPlacedPayload payload) {
        // Step 1 – load the order from Postgres
        Order order = mapper.findOrderById(payload.getOrderId());
        if (order == null) {
            throw new DeadLetterException("Order not found in DB: " + payload.getOrderId());
        }

        // Step 2 – idempotency check (status != NEW means a prior delivery already settled it)
        if (order.getStatus() != OrderStatus.NEW) {
            log.info("Order {} already in status {} — duplicate delivery, nothing to do",
                    order.getId(), order.getStatus());
            return SettlementResult.alreadyHandled(order.getId());
        }

        // Step 3 – check the instrument is still tradable
        Instrument instrument = mapper.findInstrumentById(order.getInstrumentId());
        if (instrument == null) {
            // Referential integrity issue — dead-letter so a human can investigate
            throw new DeadLetterException(
                    "Instrument " + order.getInstrumentId() + " not found for order " + order.getId());
        }

        if (!instrument.isTradable()) {
            log.info("Instrument {} not tradable, rejecting order {}", instrument.getSymbol(), order.getId());
            return settleWithRetry(order, FillDecision.reject("INSTRUMENT_NOT_TRADABLE"), instrument);
        }

        if (payload.getPrice() != null) {
            if (payload.getPrice().signum() <= 0) {
                throw new DeadLetterException("Invalid limit_price in order payload: " + payload.getPrice());
            }
            order.setLimitPrice(payload.getPrice().setScale(2, RoundingMode.HALF_UP));
        }

        // Step 4 – fetch a live quote and step 5 – apply the fill rule
        FillDecision initialDecision;
        try {
            QuotePayload quote = fauxnanceClient.getQuote(instrument.getSymbol());
            initialDecision = FillRule.apply(order, quote);
        } catch (PriceUnavailableException e) {
            // Fauxnance outage is a business outcome: reject rather than leave at NEW
            log.warn("Price unavailable for {} after retries — rejecting order {}: {}",
                    instrument.getSymbol(), order.getId(), e.getMessage());
            initialDecision = FillDecision.reject("PRICE_UNAVAILABLE");
        }

        // Steps 5–7 delegate to SettlementService (one DB transaction)
        return settleWithRetry(order, initialDecision, instrument);
    }

    

    private SettlementResult settleWithRetry(Order order, FillDecision decision,
                                              Instrument instrument) {
        for (int attempt = 1; attempt <= maxLockAttempts; attempt++) {
            try {
                return settlementService.settle(order, decision, instrument);
            } catch (OptimisticLockRetryException e) {
                log.warn("Optimistic lock conflict for order {}, attempt {}/{}",
                        order.getId(), attempt, maxLockAttempts);
                if (attempt == maxLockAttempts) {
                    throw new RecoverableDataAccessException(
                            "Exhausted optimistic lock retry budget for order " + order.getId(), e);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }
}
