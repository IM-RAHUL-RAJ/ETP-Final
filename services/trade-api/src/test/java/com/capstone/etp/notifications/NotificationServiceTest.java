package com.capstone.etp.notifications;

import com.capstone.etp.exception.AccountAccessDeniedException;
import com.capstone.etp.exception.ModuleException;
import com.capstone.etp.security.AccountAuthorizer;
import com.capstone.etp.testsupport.TestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    private InMemoryNotificationMapper ledger;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryNotificationMapper();
        service = new NotificationService(ledger, new AccountAuthorizer());
        TestIdentity.customer(1L);
    }

    @AfterEach
    void tearDown() {
        TestIdentity.clear();
    }

    private long record(long accountId, String subject) {
        return service.enqueue(UUID.randomUUID(), accountId, NotificationKind.ORDER_FILLED, subject, "body").notificationId();
    }

    @Test
    void historyContainsOnlyTheOwningAccountsMessages() {
        record(1L, "mine");
        record(2L, "someone else's");

        List<NotificationResponse> history = service.list(1L, false, null);

        assertEquals(1, history.size());
        assertEquals("mine", history.get(0).subject());
    }

    @Test
    void historyIsRefusedToAnAccountThatDoesNotOwnIt() {
        record(2L, "private");

        assertThrows(AccountAccessDeniedException.class, () -> service.list(2L, false, null));
        assertThrows(AccountAccessDeniedException.class, () -> service.unreadCount(2L));
        assertThrows(AccountAccessDeniedException.class, () -> service.markRead(2L, 101L));
    }

    @Test
    void anAdminTokenIsNotAMasterKeyToCustomerHistory() {
        TestIdentity.admin(99L);
        record(1L, "private");

        assertThrows(AccountAccessDeniedException.class, () -> service.list(1L, false, null));
    }

    @Test
    void anotherAccountsNotificationCannotBeMarkedReadThroughMyOwnPath() {
        long theirs = record(2L, "theirs");

        ModuleException refused = assertThrows(ModuleException.class, () -> service.markRead(1L, theirs));

        assertEquals("NTF-404", refused.getCatalogueCode());
        assertFalse(ledger.rows.get(theirs).getReadAt() != null, "their message must stay unread");
    }

    @Test
    void markingReadUpdatesTheCountAndFilter() {
        long id = record(1L, "one");
        record(1L, "two");
        assertEquals(2, service.unreadCount(1L));

        NotificationResponse read = service.markRead(1L, id);

        assertTrue(read.read());
        assertEquals(1, service.unreadCount(1L));
        assertEquals(1, service.list(1L, true, null).size());
    }

    @Test
    void theLimitIsClamped() {
        for (int i = 0; i < 5; i++) {
            record(1L, "n" + i);
        }
        assertEquals(1, service.list(1L, false, 0).size(), "zero is raised to one");
        assertEquals(5, service.list(1L, false, 10_000).size(), "a huge limit is capped, not honoured blindly");
    }

    @Test
    void theHistoryShowsTheResolvedChannelOnceSent() {
        long id = record(1L, "sent");
        ledger.markSent(id, com.capstone.etp.preferences.AlertChannel.SMS,
                com.capstone.etp.preferences.ChannelSource.STORED, null);

        NotificationResponse shown = service.list(1L, false, null).get(0);

        assertEquals(com.capstone.etp.preferences.AlertChannel.SMS, shown.channel());
        assertEquals(NotificationStatus.SENT, shown.status());
    }

    @Test
    void aMessageThatLooksLikeItCarriesACredentialIsNeverRecorded() {
        assertThrows(ContentGuard.UnsafeContentException.class, () -> service.enqueue(
                UUID.randomUUID(), 1L, NotificationKind.ORDER_FILLED, "Your password is hunter2", "x"));
        assertTrue(ledger.rows.isEmpty());
    }
}
