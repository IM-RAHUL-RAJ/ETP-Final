package com.capstone.etp.notifications;

import com.capstone.etp.preferences.AlertChannel;
import com.capstone.etp.preferences.ChannelResolver;
import com.capstone.etp.preferences.ChannelSource;
import com.capstone.etp.preferences.PreferencesUnavailableException;
import com.capstone.etp.preferences.ResolvedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    /** Records what each channel was asked to send. */
    static class RecordingSender implements ChannelSender {
        final AlertChannel channel;
        final List<OutboundMessage> sent = new ArrayList<>();
        Mode mode = Mode.LIVE;
        boolean failing;

        RecordingSender(AlertChannel channel) {
            this.channel = channel;
        }

        @Override
        public AlertChannel channel() {
            return channel;
        }

        @Override
        public Mode send(OutboundMessage message) throws DeliveryException {
            if (failing) {
                throw new DeliveryException("provider down", new RuntimeException("x"));
            }
            sent.add(message);
            return mode;
        }
    }

    private InMemoryNotificationMapper ledger;
    private ChannelResolver resolver;
    private RecordingSender email;
    private RecordingSender sms;
    private RecordingSender push;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        ledger = new InMemoryNotificationMapper();
        resolver = mock(ChannelResolver.class);
        // A mock answers false to everything; the customer wants every message unless a test says otherwise.
        when(resolver.wants(anyLong(), org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        when(resolver.mascotOf(anyLong())).thenReturn("OWL");
        email = new RecordingSender(AlertChannel.EMAIL);
        sms = new RecordingSender(AlertChannel.SMS);
        push = new RecordingSender(AlertChannel.PUSH);
        dispatcher = new NotificationDispatcher(ledger, resolver, List.of(email, sms, push), 50, 3, 30, 300);
    }

    private Notification queued(long accountId) {
        ledger.insertIfAbsent(UUID.randomUUID(), accountId, NotificationKind.ORDER_FILLED, "Order filled: BUY 1 AAPL", "Filled.");
        return ledger.rows.values().stream().reduce((first, second) -> second).orElseThrow();
    }

    @Test
    void sendsOnTheChannelPreferencesHoldsAndRecordsIt() {
        when(resolver.resolve(1L)).thenReturn(Optional.of(new ResolvedChannel(AlertChannel.SMS, "9876543210", ChannelSource.STORED)));
        Notification n = queued(1L);

        dispatcher.dispatch(n);

        assertEquals(1, sms.sent.size());
        assertTrue(email.sent.isEmpty());
        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertEquals(AlertChannel.SMS, n.getResolvedChannel(), "the record shows the channel, it is not a claim");
        assertEquals(ChannelSource.STORED, n.getChannelSource());
    }

    @Test
    void changingThePreferenceChangesTheChannelUsedForTheNextMessage() {
        when(resolver.resolve(1L))
                .thenReturn(Optional.of(new ResolvedChannel(AlertChannel.EMAIL, "a@b.example", ChannelSource.STORED)))
                .thenReturn(Optional.of(new ResolvedChannel(AlertChannel.SMS, "9876543210", ChannelSource.STORED)));
        Notification first = queued(1L);
        Notification second = queued(1L);

        dispatcher.dispatch(first);
        dispatcher.dispatch(second);

        assertEquals(AlertChannel.EMAIL, first.getResolvedChannel());
        assertEquals(AlertChannel.SMS, second.getResolvedChannel());
        assertEquals(1, email.sent.size());
        assertEquals(1, sms.sent.size());
    }

    @Test
    void theChannelIsAskedForOnEverySendNotCachedAtStartUp() {
        when(resolver.resolve(1L)).thenReturn(Optional.of(new ResolvedChannel(AlertChannel.PUSH, "account:1", ChannelSource.DEFAULT)));
        dispatcher.dispatch(queued(1L));
        dispatcher.dispatch(queued(1L));

        org.mockito.Mockito.verify(resolver, org.mockito.Mockito.times(2)).resolve(1L);
    }

    @Test
    void aMessageSentOnTheDefaultChannelSaysSo() {
        when(resolver.resolve(1L)).thenReturn(Optional.of(new ResolvedChannel(AlertChannel.EMAIL, "a@b.example", ChannelSource.DEFAULT)));
        Notification n = queued(1L);

        dispatcher.dispatch(n);

        assertEquals(ChannelSource.DEFAULT, n.getChannelSource());
    }

    @Test
    void aChannelWithNoProviderIsRecordedAsAStubSend() {
        sms.mode = ChannelSender.Mode.STUB;
        when(resolver.resolve(1L)).thenReturn(Optional.of(new ResolvedChannel(AlertChannel.SMS, "9876543210", ChannelSource.STORED)));
        Notification n = queued(1L);

        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertEquals(NotificationDispatcher.REASON_STUB, n.getStatusReason(), "nobody may mistake a logged stub for a live send");
    }

    // ---------------------------------------------------------------- when the channel cannot be resolved

    @Test
    void noUsableContactHoldsTheMessageAndSaysWhy() {
        when(resolver.resolve(1L)).thenReturn(Optional.empty());
        Notification n = queued(1L);

        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.HELD, n.getStatus());
        assertEquals(NotificationDispatcher.REASON_NO_CONTACT, n.getStatusReason());
        assertNull(n.getResolvedChannel());
        assertTrue(email.sent.isEmpty() && sms.sent.isEmpty() && push.sent.isEmpty(),
                "must not quietly go out on a channel the customer did not choose");
    }

    @Test
    void aPreferencesOutageKeepsTheMessageQueuedInsteadOfLosingIt() {
        when(resolver.resolve(1L)).thenThrow(new PreferencesUnavailableException("down", new RuntimeException()));
        Notification n = queued(1L);

        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.QUEUED, n.getStatus());
        assertEquals(NotificationDispatcher.REASON_PREFERENCES_UNAVAILABLE, n.getStatusReason());
    }

    @Test
    void aHeldMessageIsDeliveredOnceAContactExists() {
        when(resolver.resolve(1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ResolvedChannel(AlertChannel.SMS, "9876543210", ChannelSource.STORED)));
        Notification n = queued(1L);

        dispatcher.dispatch(n);
        assertEquals(NotificationStatus.HELD, n.getStatus());
        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.SENT, n.getStatus());
    }

    // ---------------------------------------------------------------- when the provider fails

    @Test
    void aProviderFailureIsRetriedAndThenGivenUp() {
        sms.failing = true;
        when(resolver.resolve(1L)).thenReturn(Optional.of(new ResolvedChannel(AlertChannel.SMS, "9876543210", ChannelSource.STORED)));
        Notification n = queued(1L);

        dispatcher.dispatch(n);
        assertEquals(NotificationStatus.QUEUED, n.getStatus(), "first failure: try again later");
        assertEquals(AlertChannel.SMS, n.getResolvedChannel(), "the channel that was tried is recorded");
        dispatcher.dispatch(n);
        assertEquals(NotificationStatus.QUEUED, n.getStatus());
        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.FAILED, n.getStatus(), "after max attempts (3 here) it is FAILED, visibly");
        assertEquals(NotificationDispatcher.REASON_PROVIDER_ERROR, n.getStatusReason());
    }

    @Test
    void aProviderOutageNeverBlocksConsumption() {
        // Consumption and delivery are separate: the listener records and returns without a provider.
        sms.failing = true;
        InMemoryNotificationMapper freshLedger = new InMemoryNotificationMapper();
        NotificationService service = new NotificationService(freshLedger, new com.capstone.etp.security.AccountAuthorizer());

        NotificationService.Enqueued enqueued = service.enqueue(UUID.randomUUID(), 1L, NotificationKind.ORDER_FILLED, "s", "b");

        assertTrue(enqueued.created());
        assertEquals(NotificationStatus.QUEUED, freshLedger.rows.get(enqueued.notificationId()).getStatus());
    }

    @Test
    void dispatchDueWorksThroughTheDueBatchAndSurvivesABadRow() {
        when(resolver.resolve(anyLong()))
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(Optional.of(new ResolvedChannel(AlertChannel.PUSH, "account:2", ChannelSource.DEFAULT)));
        queued(1L);
        Notification second = queued(2L);

        dispatcher.dispatchDue();

        assertEquals(NotificationStatus.SENT, second.getStatus(), "one failing row must not stop the rest");
    }

    @Test
    void aKindTheCustomerSwitchedOffIsKeptInTheInboxButNotSent() {
        when(resolver.wants(1L, "PRICE_ALERT")).thenReturn(false);
        Notification n = queued(1L);
        n.setKind(NotificationKind.PRICE_ALERT);

        dispatcher.dispatch(n);

        assertEquals(NotificationStatus.SKIPPED, ledger.rows.get(n.getId()).getStatus());
        assertEquals("OPTED_OUT", ledger.rows.get(n.getId()).getStatusReason());
        assertEquals(0, email.sent.size() + sms.sent.size() + push.sent.size());
    }
}
