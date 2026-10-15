package com.capstone.etp.preferences;

import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.AccountNotFoundException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PreferencesServiceTest {

    private PreferencesMapper mapper;
    private PreferencesService service;

    @BeforeEach
    void setUp() {
        mapper = mock(PreferencesMapper.class);
        service = new PreferencesService(mapper, new AccountAuthorizer());
        TestIdentity.customer(1L, 2L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private static PreferenceRow row(String email, String phone, AlertChannel channel, Long defaultAccount) {
        PreferenceRow row = new PreferenceRow();
        row.setClientId(10L);
        row.setEmail(email);
        row.setPhone(phone);
        row.setAlertChannel(channel);
        row.setDefaultAccountId(defaultAccount);
        row.setUpdatedAt(channel == null ? null : Instant.parse("2026-10-01T10:00:00Z"));
        return row;
    }

    // ---------------------------------------------------------------- saved and read back

    @Test
    void preferencesAreSavedAndReadBack() {
        PreferenceRow before = row("john.doe@example.com", "9876543210", null, null);
        PreferenceRow after = row("john.doe@example.com", "9876543210", AlertChannel.SMS, 2L);
        when(mapper.findByAccountId(1L)).thenReturn(before, after);
        when(mapper.countAccountOfClient(2L, 10L)).thenReturn(1);

        PreferencesResponse saved = service.update(1L, new UpdatePreferencesRequest(2L, AlertChannel.SMS));

        verify(mapper).upsert(10L, 2L, AlertChannel.SMS);
        assertEquals(AlertChannel.SMS, saved.alertChannel());
        assertEquals(2L, saved.defaultAccountId());
        assertEquals(ChannelSource.STORED, saved.source());
    }

    @Test
    void whenNothingIsStoredTheDocumentedDefaultIsShown() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", "9876543210", null, null));

        PreferencesResponse response = service.get(1L);

        assertEquals(AlertChannel.EMAIL, response.alertChannel());
        assertEquals(ChannelSource.DEFAULT, response.source());
        assertEquals(1L, response.defaultAccountId(), "the default account is the account being viewed");
    }

    @Test
    void theResponseNeverCarriesTheFullContactDetail() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", "9876543210", AlertChannel.EMAIL, 1L));

        PreferencesResponse response = service.get(1L);

        assertEquals("j***@example.com", response.contactDetail());
        String everything = response.toString();
        assertFalse(everything.contains("john.doe"), "full e-mail leaked: " + everything);
        assertFalse(everything.contains("9876543210"), "full phone leaked: " + everything);
        assertTrue(response.channels().stream().anyMatch(o -> o.channel() == AlertChannel.SMS && o.available()));
    }

    // ---------------------------------------------------------------- access control

    @Test
    void anotherAccountsPreferencesAreRefusedNotReturnedEmpty() {
        assertThrows(AccountAccessDeniedException.class, () -> service.get(5L));
        assertThrows(AccountAccessDeniedException.class,
                () -> service.update(5L, new UpdatePreferencesRequest(5L, AlertChannel.EMAIL)));
        verify(mapper, never()).findByAccountId(anyLong());
        verify(mapper, never()).upsert(anyLong(), anyLong(), any());
    }

    @Test
    void anAdminTokenGetsNoAccessToACustomersPreferences() {
        TestIdentity.admin(99L);

        assertThrows(AccountAccessDeniedException.class, () -> service.get(1L));
    }

    @Test
    void theDefaultAccountMustBeOneTheSignInMayUse() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, null, null));

        assertThrows(AccountAccessDeniedException.class,
                () -> service.update(1L, new UpdatePreferencesRequest(5L, AlertChannel.EMAIL)));
        verify(mapper, never()).upsert(anyLong(), anyLong(), any());
    }

    @Test
    void theDefaultAccountMustBelongToTheSameCustomer() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, null, null));
        when(mapper.countAccountOfClient(2L, 10L)).thenReturn(0);

        ModuleException refused = assertThrows(ModuleException.class,
                () -> service.update(1L, new UpdatePreferencesRequest(2L, AlertChannel.EMAIL)));

        assertEquals("VAL-422", refused.getCatalogueCode());
    }

    @Test
    void aChannelTheProfileCannotServeIsRefusedAtOnce() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, null, null));
        when(mapper.countAccountOfClient(1L, 10L)).thenReturn(1);

        ModuleException refused = assertThrows(ModuleException.class,
                () -> service.update(1L, new UpdatePreferencesRequest(1L, AlertChannel.SMS)));

        assertEquals("VAL-422", refused.getCatalogueCode());
        verify(mapper, never()).upsert(anyLong(), anyLong(), any());
    }

    @Test
    void anUnknownAccountIsNotFound() {
        TestIdentity.customer(77L);
        when(mapper.findByAccountId(77L)).thenReturn(null);

        assertThrows(AccountNotFoundException.class, () -> service.get(77L));
    }

    // ---------------------------------------------------------------- the seam: ChannelResolver

    @Test
    void resolveReturnsTheStoredChannelWithItsDestination() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", "9876543210", AlertChannel.SMS, 1L));

        ResolvedChannel resolved = service.resolve(1L).orElseThrow();

        assertEquals(AlertChannel.SMS, resolved.channel());
        assertEquals("9876543210", resolved.destination());
        assertEquals(ChannelSource.STORED, resolved.source());
    }

    @Test
    void resolveWorksWithNoTokenBecauseItIsNotARoute() {
        TestIdentity.clear();
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, AlertChannel.EMAIL, 1L));

        assertTrue(service.resolve(1L).isPresent());
    }

    @Test
    void resolveFallsBackToEmailWhenNothingIsStored() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, null, null));

        ResolvedChannel resolved = service.resolve(1L).orElseThrow();

        assertEquals(AlertChannel.EMAIL, resolved.channel());
        assertEquals("john.doe@example.com", resolved.destination());
        assertEquals(ChannelSource.DEFAULT, resolved.source());
    }

    @Test
    void resolveFallsBackToPushWhenNothingIsStoredAndThereIsNoEmail() {
        when(mapper.findByAccountId(1L)).thenReturn(row(null, null, null, null));

        ResolvedChannel resolved = service.resolve(1L).orElseThrow();

        assertEquals(AlertChannel.PUSH, resolved.channel());
        assertEquals(ChannelSource.DEFAULT, resolved.source());
    }

    @Test
    void resolveDoesNotSwitchChannelWhenTheChosenOneHasNoContact() {
        when(mapper.findByAccountId(1L)).thenReturn(row("john.doe@example.com", null, AlertChannel.SMS, 1L));

        assertEquals(Optional.empty(), service.resolve(1L),
                "must be empty, not silently e-mail: the customer chose SMS");
    }

    @Test
    void resolveOfAnUnknownAccountIsEmpty() {
        when(mapper.findByAccountId(9L)).thenReturn(null);

        assertEquals(Optional.empty(), service.resolve(9L));
    }

    @Test
    void resolveRaisesOnlyWhenTheStoreCannotBeRead() {
        when(mapper.findByAccountId(1L)).thenThrow(new IllegalStateException("db down"));

        assertThrows(PreferencesUnavailableException.class, () -> service.resolve(1L));
    }

    @Test
    void aResolvedChannelNeverPrintsItsDestination() {
        ResolvedChannel resolved = new ResolvedChannel(AlertChannel.EMAIL, "john.doe@example.com", ChannelSource.STORED);

        assertFalse(resolved.toString().contains("john.doe"));
    }
}