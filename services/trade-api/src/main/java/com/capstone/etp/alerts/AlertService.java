package com.capstone.etp.alerts;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.notifications.NotificationStatusLookup;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.service.AccountService;
import com.capstone.etp.service.MarketQuoteCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;


@Slf4j
@Service
public class AlertService {

    static final int TRIGGER_HISTORY_LIMIT = 100;

    private final AlertMapper mapper;
    private final AlertIndex index;
    private final AccountAuthorizer authorizer;
    private final AccountService accountService;
    private final AccountMapper accountMapper;
    private final MarketQuoteCache quoteCache;
    private final NotificationStatusLookup notificationStatuses;
    private final int cap;

    AlertService(AlertMapper mapper,
                 AlertIndex index,
                 AccountAuthorizer authorizer,
                 AccountService accountService,
                 AccountMapper accountMapper,
                 MarketQuoteCache quoteCache,
                 NotificationStatusLookup notificationStatuses,
                 @Value("${app.alerts.max-per-account:25}") int cap) {
        this.mapper = mapper;
        this.index = index;
        this.authorizer = authorizer;
        this.accountService = accountService;
        this.accountMapper = accountMapper;
        this.quoteCache = quoteCache;
        this.notificationStatuses = notificationStatuses;
        this.cap = cap;
    }

    public List<AlertResponse> list(Long accountId) {
        authorizer.ensureOwnAccount(accountId);
        return mapper.findByAccount(accountId).stream().map(AlertResponse::from).toList();
    }

    public AlertResponse create(Long accountId, CreateAlertRequest request) {
        authorizer.ensureOwnAccount(accountId);
        accountService.validateAccountId(accountId);

        String symbol = requireKnownSymbol(request.symbol());
        refuseIfAlreadyMet(symbol, request.condition(), request.price());

        Long id = mapper.insertWithinCap(accountId, symbol, request.condition().direction(), request.condition(),
                request.price(), request.isEnabled(), cap);
        if (id == null) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "You can keep at most " + cap + " alerts. Remove one to add another");
        }

        PriceAlert created = mapper.findByIdAndAccount(id, accountId);
        index.add(created);
        log.info("[ALERTS] created alert {} accountId={} symbol={} {} {}",
                id, accountId, symbol, request.condition().symbol(), request.price());
        return AlertResponse.from(created);
    }

    /** Changes what is present in the request. An edit arms the alert again if it had fired. */
    public AlertResponse update(Long accountId, Long alertId, UpdateAlertRequest request) {
        authorizer.ensureOwnAccount(accountId);
        PriceAlert current = requireAlert(accountId, alertId);

        String symbol = request.symbol() == null ? current.getSymbol() : requireKnownSymbol(request.symbol());
        AlertOperator operator = request.condition() == null ? current.getOperator() : request.condition();
        BigDecimal price = request.price() == null ? current.getThreshold() : request.price();
        boolean enabled = request.enabled() == null ? current.isEnabled() : request.enabled();

        boolean changesCondition = !symbol.equals(current.getSymbol()) || operator != current.getOperator()
                || price.compareTo(current.getThreshold()) != 0;
        if (enabled && changesCondition) {
            refuseIfAlreadyMet(symbol, operator, price);
        }

        if (mapper.updateCondition(alertId, accountId, symbol, operator.direction(), operator, price, enabled) == 0) {
            throw notChangeable();
        }
        PriceAlert after = mapper.findByIdAndAccount(alertId, accountId);
        index.replace(current, after);
        return AlertResponse.from(after);
    }

    /** Switches an alert on or off without touching its condition or history. */
    public AlertResponse setEnabled(Long accountId, Long alertId, boolean enabled) {
        authorizer.ensureOwnAccount(accountId);
        PriceAlert current = requireAlert(accountId, alertId);
        if (mapper.setEnabled(alertId, accountId, enabled) == 0) {
            throw notChangeable();
        }
        PriceAlert after = mapper.findByIdAndAccount(alertId, accountId);
        index.replace(current, after);
        return AlertResponse.from(after);
    }

    /** What happened each time this alert fired, newest first. Creating the alert is not part of it. */
    public AlertTriggersResponse triggers(Long accountId, Long alertId) {
        authorizer.ensureOwnAccount(accountId);
        requireAlert(accountId, alertId);

        List<AlertTrigger> rows = mapper.findTriggers(alertId, TRIGGER_HISTORY_LIMIT);
        List<Long> notificationIds = rows.stream().map(AlertTrigger::getNotificationId)
                .filter(java.util.Objects::nonNull).toList();
        Map<Long, String> statuses = notificationIds.isEmpty() ? Map.of() : notificationStatuses.statusOf(notificationIds);

        return new AlertTriggersResponse(alertId, rows.stream()
                .map(row -> new AlertTriggersResponse.Trigger(
                        row.getId(),
                        row.getTriggeredAt(),
                        row.getTriggerPrice(),
                        row.getNotificationId() == null ? "UNKNOWN" : statuses.getOrDefault(row.getNotificationId(), "UNKNOWN"),
                        row.getNotificationId()))
                .toList());
    }

    /** Removes the alert and its history. */
    public AlertResponse remove(Long accountId, Long alertId) {
        authorizer.ensureOwnAccount(accountId);
        PriceAlert alert = requireAlert(accountId, alertId);
        if (mapper.delete(alertId, accountId) == 0) {
            throw new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                    "This alert fired and is still being handed over for delivery, so it cannot be removed yet");
        }
        index.remove(alert.getSymbol(), alertId);
        return AlertResponse.from(alert);
    }

    private PriceAlert requireAlert(Long accountId, Long alertId) {
        PriceAlert alert = mapper.findByIdAndAccount(alertId, accountId);
        if (alert == null) {
            throw new ModuleException(HttpStatus.NOT_FOUND, "ALR-404", "Alert not found");
        }
        return alert;
    }

    private String requireKnownSymbol(String raw) {
        String symbol = raw.trim().toUpperCase(Locale.ROOT);
        if (accountMapper.countInstrumentsBySymbol(symbol) == 0) {
            throw new InstrumentNotFoundException(symbol, InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }
        return symbol;
    }

    /**
     * An alert whose condition already holds would fire on the next quote and tell the customer nothing
     * they did not know. Refuse it and say why. An equality alert is never refused: it is a level to watch for.
     */
    private void refuseIfAlreadyMet(String symbol, AlertOperator operator, BigDecimal price) {
        if (operator == AlertOperator.EQ) {
            return;
        }
        Optional<MarketQuote> current = quoteCache.get(symbol);
        if (current.isPresent() && current.get().getPrice() != null
                && !Boolean.TRUE.equals(current.get().getStale())
                && operator.matches(current.get().getPrice(), price)) {
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "VAL-422",
                    "The price of " + symbol + " already satisfies " + operator.symbol() + " " + price.stripTrailingZeros().toPlainString()
                            + ". Choose a level the price has not reached yet");
        }
    }

    private static ModuleException notChangeable() {
        return new ModuleException(HttpStatus.CONFLICT, "ALR-409",
                "This alert fired and is still being handed over for delivery, so it cannot be changed yet");
    }
}
