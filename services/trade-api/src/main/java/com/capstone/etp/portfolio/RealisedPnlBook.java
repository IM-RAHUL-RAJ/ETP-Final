package com.capstone.etp.portfolio;

import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.kafka.MalformedEventException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.util.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class RealisedPnlBook {

    private final RealisedPnlMapper ledger;
    private final AccountMapper accountMapper;

    
    public boolean bookSale(UUID eventId, long accountId, String symbol, int quantity,
                            BigDecimal executedPrice, BigDecimal averageCost, Instant bookedAt) {
        BalanceResponse balance = accountMapper.getBalance(accountId);
        String baseCurrency = balance == null ? null : trim(balance.getCurrency());
        String instrumentCurrency = trim(accountMapper.getInstrumentCurrency(symbol));
        if (baseCurrency == null || instrumentCurrency == null) {
            throw new MalformedEventException("Sale refers to an unknown account or instrument");
        }

        BigDecimal averageCostBase;
        try {
            averageCostBase = baseCurrency.equalsIgnoreCase(instrumentCurrency)
                    ? averageCost
                    : CurrencyConverter.convert(instrumentCurrency, baseCurrency, averageCost);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Sale is in a currency this platform cannot convert", e);
        }

        BigDecimal realised = executedPrice.subtract(averageCostBase).multiply(BigDecimal.valueOf(quantity));
        int inserted = ledger.insertIfAbsent(eventId, accountId, symbol, quantity, executedPrice,
                averageCostBase, baseCurrency, realised, bookedAt);
        if (inserted == 0) {
            log.info("[PORTFOLIO] sale already booked, ignored eventId={}", eventId);
            return false;
        }
        log.info("[PORTFOLIO] booked realised P&L eventId={} accountId={} symbol={} quantity={} realised={}",
                eventId, accountId, symbol, quantity, realised);
        return true;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
