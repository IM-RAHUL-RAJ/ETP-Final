package com.capstone.etp.funds;

import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.notifications.NotificationService;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Adding and withdrawing cash. The balance change is one conditional UPDATE, so two requests can never
 * overdraw the account, and the movement is recorded in the same transaction. The customer is told in the inbox.
 */
@Slf4j
@Service
public class FundsService {

    private final FundsMapper mapper;
    private final AccountAuthorizer authorizer;
    private final NotificationService notifications;
    private final BigDecimal maxPerMovement;

    FundsService(FundsMapper mapper,
                 AccountAuthorizer authorizer,
                 NotificationService notifications,
                 @Value("${app.funds.max-per-movement:1000000}") BigDecimal maxPerMovement) {
        this.mapper = mapper;
        this.authorizer = authorizer;
        this.notifications = notifications;
        this.maxPerMovement = maxPerMovement;
    }

    @Transactional
    public FundsResponse deposit(Long accountId, FundsRequest request) {
        authorizer.ensureOwnAccount(accountId);
        limit(request.amount());
        String currency = requireCurrency(accountId);

        BigDecimal balance = mapper.deposit(accountId, request.amount());
        if (balance == null) {
            throw notActive(accountId);
        }
        return record(accountId, FundsKind.DEPOSIT, request, currency, balance);
    }

    @Transactional
    public FundsResponse withdraw(Long accountId, FundsRequest request) {
        authorizer.ensureOwnAccount(accountId);
        limit(request.amount());
        String currency = requireCurrency(accountId);

        BigDecimal balance = mapper.withdraw(accountId, request.amount());
        if (balance == null) {
            // Either the account is not active, or it holds less than was asked for.
            if (!"ACTIVE".equals(mapper.statusOf(accountId))) {
                throw notActive(accountId);
            }
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "FND-422",
                    "You can withdraw up to your available cash balance");
        }
        return record(accountId, FundsKind.WITHDRAWAL, request, currency, balance);
    }

    public List<FundsResponse> history(Long accountId, Integer limit) {
        authorizer.ensureOwnAccount(accountId);
        int bounded = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return mapper.findByAccount(accountId, bounded).stream()
                .map(m -> new FundsResponse(m.getId(), m.getAccountId(), m.getKind(), m.getAmount(), m.getCurrency(),
                        m.getMethod(), m.getBalanceAfter(), m.getCreatedAt()))
                .toList();
    }

    private FundsResponse record(Long accountId, FundsKind kind, FundsRequest request, String currency, BigDecimal balance) {
        UUID id = UUID.randomUUID();
        mapper.insertMovement(id, accountId, kind, request.amount(), currency, request.methodOrDefault(), balance);
        log.info("[FUNDS] {} accountId={} amount={} {} method={}", kind, accountId, request.amount(), currency, request.method());
        announce(id, accountId, kind, request.amount(), currency, balance);
        return new FundsResponse(id, accountId, kind, request.amount(), currency, request.methodOrDefault(), balance, java.time.Instant.now());
    }

    /** A failed message must never undo the money movement, so it is logged and dropped. */
    private void announce(UUID id, Long accountId, FundsKind kind, BigDecimal amount, String currency, BigDecimal balance) {
        try {
            String verb = kind == FundsKind.DEPOSIT ? "added to" : "withdrawn from";
            notifications.recordFundsEvent(id, accountId,
                    (kind == FundsKind.DEPOSIT ? "Funds added: " : "Withdrawal requested: ") + amount.toPlainString() + " " + currency,
                    amount.toPlainString() + " " + currency + " was " + verb + " your account. Your cash balance is now "
                            + balance.toPlainString() + " " + currency + ".");
        } catch (RuntimeException e) {
            log.warn("[FUNDS] could not record the inbox message for movement {}: {}", id, e.getMessage());
        }
    }

    private String requireCurrency(Long accountId) {
        String currency = mapper.currencyOf(accountId);
        if (currency == null) {
            throw new AccountNotFoundException(accountId);
        }
        return currency.trim();
    }

    private void limit(BigDecimal amount) {
        if (amount.compareTo(maxPerMovement) > 0) {
            throw new ModuleException(HttpStatus.UNPROCESSABLE_ENTITY, "FND-422",
                    "A single movement can be at most " + maxPerMovement.toPlainString());
        }
    }

    private static ModuleException notActive(Long accountId) {
        return new ModuleException(HttpStatus.FORBIDDEN, "ACC-403", "Your account is not active, so funds cannot be moved");
    }
}
