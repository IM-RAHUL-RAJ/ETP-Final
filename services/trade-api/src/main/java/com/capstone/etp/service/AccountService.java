package com.capstone.etp.service;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.BalanceResponse;
import com.capstone.etp.dto.InstrumentResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.dto.OrderHistoryEntry;
import com.capstone.etp.dto.PositionResponse;
import com.capstone.etp.enums.OrderStatus;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;
    private final AccountAuthorizer accountAuthorizer;

    // added exception handling to throw AccountNotFoundException if an account is not found
    public void validateAccountId(Long accountId) throws AccountNotFoundException{
        AccountResponse account = accountMapper.getAccountById(accountId);
        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }
    }

    // Service method to retrieve account details by account ID
    public AccountResponse getAccount(Long accountId) {
        accountAuthorizer.ensureAccountAccess(accountId);
        AccountResponse account = accountMapper.getAccountById(accountId);
        validateAccountId(accountId);
        return account;
    }

    //Retrieve account balance information
    public BalanceResponse getBalance(Long accountId) {
        accountAuthorizer.ensureAccountAccess(accountId);
        validateAccountId(accountId);
        BalanceResponse balance = accountMapper.getBalance(accountId);
        return balance;
    }

    //Retrieve all positions held in the account's portfolio
    public List<PositionResponse> getPositions(Long accountId) {
        accountAuthorizer.ensureAccountAccess(accountId);
        validateAccountId(accountId);
        return accountMapper.getPositionsByAccountId(accountId);
    }

    //Retrieve order history for an account with optional filtering by status and date range
    public List<OrderHistoryEntry> getOrders(Long accountId, OrderStatus status, Instant from, Instant to) {
        accountAuthorizer.ensureAccountAccess(accountId);
        validateAccountId(accountId);
        return accountMapper.getOrdersByAccountId(accountId, status, from, to);
    }

    // Every instrument, with its currency. Read-only reference data for any signed-in user.
    public List<InstrumentResponse> getInstruments() {
        return accountMapper.getInstruments();
    }

    public List<WatchlistEntry> getWatchlist(Long accountId) {
        accountAuthorizer.ensureAccountAccess(accountId);
        validateAccountId(accountId);
        return accountMapper.getWatchlist(accountId);
    }

    // Idempotent: adding a symbol that is already watched is not an error.
    public List<WatchlistEntry> addToWatchlist(Long accountId, String symbol) {
        accountAuthorizer.ensureAccountAccess(accountId);
        accountAuthorizer.ensureOrderManagementAllowed();
        validateAccountId(accountId);
        String normalised = symbol == null ? "" : symbol.trim().toUpperCase();
        if (accountMapper.countInstrumentsBySymbol(normalised) == 0) {
            throw new InstrumentNotFoundException(normalised, InstrumentNotFoundException.Reason.UNKNOWN_SYMBOL);
        }
        accountMapper.addToWatchlist(accountId, normalised);
        return accountMapper.getWatchlist(accountId);
    }

    // Idempotent: removing a symbol that is not watched is not an error.
    public List<WatchlistEntry> removeFromWatchlist(Long accountId, String symbol) {
        accountAuthorizer.ensureAccountAccess(accountId);
        accountAuthorizer.ensureOrderManagementAllowed();
        validateAccountId(accountId);
        String normalised = symbol == null ? "" : symbol.trim().toUpperCase();
        accountMapper.removeFromWatchlist(accountId, normalised);
        return accountMapper.getWatchlist(accountId);
    }
}
