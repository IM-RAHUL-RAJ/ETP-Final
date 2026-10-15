package com.capstone.etp.portfolio;

import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.util.CurrencyConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;


@Slf4j
@Service
public class PortfolioService {

    private static final int MONEY_SCALE = 2;

    private final AccountService accountService;
    private final AccountAuthorizer authorizer;
    private final PricingService pricing;
    private final RealisedPnlMapper ledger;
    private final Clock clock;

    PortfolioService(AccountService accountService,
                     AccountAuthorizer authorizer,
                     PricingService pricing,
                     RealisedPnlMapper ledger,
                     Clock clock) {
        this.accountService = accountService;
        this.authorizer = authorizer;
        this.pricing = pricing;
        this.ledger = ledger;
        this.clock = clock;
    }


    public PortfolioSummaryResponse summary(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        Valuation valuation = value(accountId, null);

        BigDecimal realised = money(ledger.sumRealised(accountId, null, null));
        BigDecimal marketValue = valuation.pricedMarketValueBase;
        BigDecimal unrealised = marketValue.subtract(valuation.pricedCostBasisBase);

        return new PortfolioSummaryResponse(
                accountId,
                valuation.baseCurrency,
                valuation.cash,
                marketValue,
                valuation.costBasisBase,
                unrealised,
                percent(unrealised, valuation.pricedCostBasisBase),
                realised,
                valuation.cash.add(marketValue),
                valuation.rows.size(),
                valuation.partial,
                valuation.asOf,
                valuation.dayChangeKnown ? valuation.dayChangeBase : null,
                valuation.dayChangeKnown ? percent(valuation.dayChangeBase, valuation.previousValueBase) : null);
    }

    public List<PricedPositionResponse> positions(Long accountId, String symbol) {
        authorizer.ensureOwnAccount(accountId);
        String filter = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        return value(accountId, filter).rows.stream().map(Row::response).toList();
    }

    public PnlResponse pnl(Long accountId, LocalDate from, LocalDate to, boolean bySymbol) {
        authorizer.ensureOwnAccount(accountId);
        if (from != null && to != null && from.isAfter(to)) {
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422", "Invalid input");
        }
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Valuation valuation = value(accountId, null);
        BigDecimal realised = money(ledger.sumRealised(accountId, fromInstant, toInstant));
        BigDecimal unrealised = valuation.pricedMarketValueBase.subtract(valuation.pricedCostBasisBase);

        List<PnlResponse.SymbolPnl> breakdown = null;
        if (bySymbol) {
            Map<String, BigDecimal> realisedBySymbol = new HashMap<>();
            for (RealisedPnlMapper.SymbolRealised row : ledger.sumRealisedBySymbol(accountId, fromInstant, toInstant)) {
                realisedBySymbol.put(row.getSymbol(), money(row.getRealised()));
            }
            Map<String, BigDecimal> unrealisedBySymbol = new HashMap<>();
            for (Row row : valuation.rows) {
                if (row.unrealisedBase != null) {
                    unrealisedBySymbol.put(row.response.symbol(), row.unrealisedBase);
                }
            }
            // Every symbol that has either booked sales in the window or an open position.
            TreeSet<String> symbols = new TreeSet<>(realisedBySymbol.keySet());
            symbols.addAll(unrealisedBySymbol.keySet());
            breakdown = new ArrayList<>();
            for (String symbol : symbols) {
                BigDecimal r = realisedBySymbol.getOrDefault(symbol, money(BigDecimal.ZERO));
                BigDecimal u = unrealisedBySymbol.getOrDefault(symbol, money(BigDecimal.ZERO));
                breakdown.add(new PnlResponse.SymbolPnl(symbol, r, u, r.add(u)));
            }
        }

        return new PnlResponse(accountId, valuation.baseCurrency, from, to, realised, unrealised,
                realised.add(unrealised), breakdown, valuation.asOf);
    }


    private record Row(PricedPositionResponse response, BigDecimal unrealisedBase) {
    }

    private static final class Valuation {
        String baseCurrency;
        BigDecimal cash;
        List<Row> rows = new ArrayList<>();
        BigDecimal costBasisBase = money(BigDecimal.ZERO);
        BigDecimal pricedCostBasisBase = money(BigDecimal.ZERO);
        BigDecimal pricedMarketValueBase = money(BigDecimal.ZERO);
        boolean partial;
        Instant asOf;
        /** Today's move: what the holdings are worth now minus what they were worth at yesterday's close. */
        BigDecimal dayChangeBase = money(BigDecimal.ZERO);
        BigDecimal previousValueBase = money(BigDecimal.ZERO);
        boolean dayChangeKnown;
    }

    private Valuation value(Long accountId, String symbolFilter) {
        BalanceResponse balance = accountService.getBalance(accountId);
        List<PositionResponse> open = accountService.getPositions(accountId).stream()
                .filter(position -> position.getQuantity() != null && position.getQuantity() > 0)
                .filter(position -> symbolFilter == null || position.getSymbol().equalsIgnoreCase(symbolFilter))
                .sorted(Comparator.comparing(PositionResponse::getSymbol))
                .toList();

        Valuation valuation = new Valuation();
        valuation.baseCurrency = balance.getCurrency().trim().toUpperCase(Locale.ROOT);
        valuation.cash = money(balance.getCashBalance());
        valuation.asOf = clock.instant();

        if (open.isEmpty()) {
            return valuation;
        }

        Map<String, String> currencies = accountService.getInstruments().stream()
                .collect(Collectors.toMap(InstrumentResponse::getSymbol,
                        instrument -> instrument.getCurrency().trim().toUpperCase(Locale.ROOT), (a, b) -> a));
        Map<String, PricingService.PricedQuote> quotes =
                pricing.price(open.stream().map(PositionResponse::getSymbol).toList());

        if (quotes.isEmpty()) {
            throw new PricingUnavailableException();
        }

        for (PositionResponse position : open) {
            valuation.rows.add(priceOne(accountId, position, currencies, quotes, valuation));
        }
        return valuation;
    }

    private Row priceOne(Long accountId, PositionResponse position, Map<String, String> currencies,
                         Map<String, PricingService.PricedQuote> quotes, Valuation valuation) {
        String symbol = position.getSymbol();
        String currency = currencies.getOrDefault(symbol, valuation.baseCurrency);
        int quantity = position.getQuantity();
        BigDecimal averageCost = position.getAverageCost();
        BigDecimal costBasis = money(averageCost.multiply(BigDecimal.valueOf(quantity)));

        PricingService.PricedQuote quote = quotes.get(symbol.trim().toUpperCase(Locale.ROOT));
        BigDecimal costBase = convert(costBasis, currency, valuation.baseCurrency);
        if (costBase == null) {
            // Cannot be expressed in the base currency: kept visible, left out of every total.
            valuation.partial = true;
        } else {
            valuation.costBasisBase = valuation.costBasisBase.add(costBase);
        }

        if (quote == null) {
            valuation.partial = true;
            return new Row(new PricedPositionResponse(accountId, symbol, quantity, averageCost, costBasis,
                    null, null, null, null, currency, null, true), null);
        }

        BigDecimal marketValue = money(quote.price().multiply(BigDecimal.valueOf(quantity)));
        BigDecimal unrealised = marketValue.subtract(costBasis);
        BigDecimal marketValueBase = convert(marketValue, currency, valuation.baseCurrency);

        BigDecimal unrealisedBase = null;
        if (costBase != null && marketValueBase != null) {
            valuation.pricedCostBasisBase = valuation.pricedCostBasisBase.add(costBase);
            valuation.pricedMarketValueBase = valuation.pricedMarketValueBase.add(marketValueBase);
            unrealisedBase = marketValueBase.subtract(costBase);
        } else {
            valuation.partial = true;
        }

        if (quote.previousClose() != null) {
            BigDecimal previousValue = money(quote.previousClose().multiply(BigDecimal.valueOf(quantity)));
            BigDecimal previousBase = convert(previousValue, currency, valuation.baseCurrency);
            if (previousBase != null && marketValueBase != null) {
                valuation.dayChangeBase = valuation.dayChangeBase.add(marketValueBase.subtract(previousBase));
                valuation.previousValueBase = valuation.previousValueBase.add(previousBase);
                valuation.dayChangeKnown = true;
            }
        }

        return new Row(new PricedPositionResponse(accountId, symbol, quantity, averageCost, costBasis,
                quote.price(), marketValue, unrealised, percent(unrealised, costBasis), currency,
                quote.asOf(), quote.stale()), unrealisedBase);
    }

    private static BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equalsIgnoreCase(to)) {
            return amount;
        }
        try {
            return CurrencyConverter.convert(from, to, amount.abs()).multiply(BigDecimal.valueOf(amount.signum()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return null;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(base, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
