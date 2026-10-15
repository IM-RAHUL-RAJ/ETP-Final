package com.capstone.etp.service;

import com.capstone.etp.dto.AccountResponse;
import com.capstone.etp.dto.WatchlistEntry;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.InstrumentNotFoundException;
import com.capstone.etp.mapper.AccountMapper;
import com.capstone.etp.security.AccountAuthorizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceWatchlistTest {

    private static final Long ACCOUNT_ID = 1L;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private AccountAuthorizer accountAuthorizer;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountMapper, accountAuthorizer);
    }

    private void accountExists() {
        when(accountMapper.getAccountById(ACCOUNT_ID)).thenReturn(AccountResponse.builder().id(ACCOUNT_ID).build());
    }

    private WatchlistEntry entry(String symbol) {
        return WatchlistEntry.builder().symbol(symbol).currency("USD").tradable(true).build();
    }

    @Test
    void getWatchlistChecksAccessAndReturnsTheAccountsEntries() {
        accountExists();
        when(accountMapper.getWatchlist(ACCOUNT_ID)).thenReturn(List.of(entry("AAPL"), entry("MSFT")));

        List<WatchlistEntry> result = accountService.getWatchlist(ACCOUNT_ID);

        assertThat(result).extracting(WatchlistEntry::getSymbol).containsExactly("AAPL", "MSFT");
        verify(accountAuthorizer).ensureAccountAccess(ACCOUNT_ID);
    }

    @Test
    void getWatchlistForAnUnknownAccountIsNotFound() {
        when(accountMapper.getAccountById(ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> accountService.getWatchlist(ACCOUNT_ID))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void addNormalisesTheSymbolAndReturnsTheUpdatedList() {
        accountExists();
        when(accountMapper.countInstrumentsBySymbol("AAPL")).thenReturn(1);
        when(accountMapper.getWatchlist(ACCOUNT_ID)).thenReturn(List.of(entry("AAPL")));

        List<WatchlistEntry> result = accountService.addToWatchlist(ACCOUNT_ID, "  aapl ");

        verify(accountMapper).addToWatchlist(ACCOUNT_ID, "AAPL");
        verify(accountAuthorizer).ensureOrderManagementAllowed();
        assertThat(result).hasSize(1);
    }

    @Test
    void addingAnUnknownSymbolIsRejectedAndWritesNothing() {
        accountExists();
        when(accountMapper.countInstrumentsBySymbol("NOPE")).thenReturn(0);

        assertThatThrownBy(() -> accountService.addToWatchlist(ACCOUNT_ID, "nope"))
                .isInstanceOf(InstrumentNotFoundException.class);

        verify(accountMapper, never()).addToWatchlist(ACCOUNT_ID, "NOPE");
    }

    @Test
    void removeDeletesBySymbolAndReturnsTheUpdatedList() {
        accountExists();
        when(accountMapper.getWatchlist(ACCOUNT_ID)).thenReturn(List.of());

        List<WatchlistEntry> result = accountService.removeFromWatchlist(ACCOUNT_ID, "msft");

        verify(accountMapper).removeFromWatchlist(ACCOUNT_ID, "MSFT");
        assertThat(result).isEmpty();
    }
}
