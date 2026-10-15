package com.capstone.etp.service;

import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final AccountMapper accountMapper;
    private final AccountAuthorizer accountAuthorizer;
    private final MarketQuoteCache cache;

    /** The latest known quote for each instrument on the account's watchlist. Symbols not yet quoted are left out. */
    public List<MarketQuote> getWatchlistQuotes(Long accountId) {
        accountAuthorizer.ensureAccountAccess(accountId);
        if (accountMapper.getAccountById(accountId) == null) {
            throw new AccountNotFoundException(accountId);
        }
        return accountMapper.getWatchlist(accountId).stream()
                .map(WatchlistEntry::getSymbol)
                .map(cache::get)
                .flatMap(java.util.Optional::stream)
                .toList();
    }
}
