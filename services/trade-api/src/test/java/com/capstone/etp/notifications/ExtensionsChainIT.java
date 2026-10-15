package com.capstone.etp.notifications;

import com.capstone.etp.TradeApiApplication;
import com.capstone.etp.kafka.consumer.QuoteObserver;
import com.capstone.etp.dto.MarketQuote;
import com.capstone.etp.alerts.AlertMapper;
import com.capstone.etp.portfolio.PricingService;
import com.capstone.etp.portfolio.RealisedPnlBook;
import com.capstone.etp.security.JwtTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(classes = TradeApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.auth.jwt.secret=integration-test-secret-integration-test-secret",
                "app.market-data.consumer.enabled=false",
                "app.notifications.consumer.enabled=false",
                "app.portfolio.consumer.enabled=false",
                "app.notifications.dispatch-interval-ms=3600000",
                "app.alerts.handover-retry-ms=3600000",
                "app.alerts.max-per-account=3",
                "app.portfolio.quote-ttl-seconds=300",
                "app.portfolio.provider-cooldown-seconds=0",
                "app.fauxnance.api-key=integration-key",
                "spring.kafka.bootstrap-servers=localhost:1"
        })
class ExtensionsChainIT {

    private static final String SECRET = "integration-test-secret-integration-test-secret";
    private static final ObjectMapper JSON = new ObjectMapper();

    // ---- a stand-in for Fauxnance, served over real HTTP so the real client code runs
    private static HttpServer fauxnance;
    private static volatile String quotesBody = "{\"data\":{\"quotes\":[]}}";
    private static final AtomicInteger quoteCalls = new AtomicInteger();

    @BeforeAll
    static void startFauxnance() throws IOException {
        fauxnance = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fauxnance.createContext("/quotes", exchange -> {
            quoteCalls.incrementAndGet();
            byte[] bytes = quotesBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fauxnance.createContext("/usage", exchange -> {
            byte[] bytes = "{\"quotaRemaining\":1873}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        fauxnance.start();
    }

    @AfterAll
    static void stopFauxnance() {
        fauxnance.stop(0);
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (fauxnance == null) {
            startFauxnance();
        }
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres"));
        registry.add("app.fauxnance.base-url", () -> "http://127.0.0.1:" + fauxnance.getAddress().getPort());
    }

    @LocalServerPort
    int port;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TradeEventListener tradeEvents;
    @Autowired
    NotificationDispatcher dispatcher;
    @Autowired
    List<QuoteObserver> quoteObservers;
    @Autowired
    RealisedPnlBook realisedBook;
    @Autowired
    PricingService pricing;
    @Autowired
    AlertMapper alertMapper;

    @Autowired
    com.capstone.etp.portfolio.PortfolioSnapshotMapper snapshotMapper;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void cleanModuleTables() {
        jdbc.execute("TRUNCATE notifications, cash_movements, price_alert_triggers, price_alerts, portfolio_realised_pnl, customer_preferences RESTART IDENTITY");
        quoteCalls.set(0);
        quotesBody = "{\"data\":{\"quotes\":[]}}";
        // the pricing cache is the one piece of state that outlives a test: start each with it empty
        ((java.util.Map<?, ?>) ReflectionTestUtils.getField(pricing, "cache")).clear();
        ReflectionTestUtils.setField(pricing, "providerUnavailableUntil", Instant.MIN);
    }

    // ---------------------------------------------------------------- helpers

    private record Reply(int status, JsonNode body) {
    }

    private static String token(long accountId) {
        Instant now = Instant.now();
        return JwtTestFixture.token(SECRET, "auth-service", accountId, List.of("CUSTOMER"), now, now.plusSeconds(900));
    }

    private Reply call(String method, String path, Long asAccount, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (asAccount != null) {
            request.header("Authorization", "Bearer " + token(asAccount));
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body()));
    }

    private static String quoteFor(String symbol, String price, boolean stale) {
        return "{\"symbol\":\"" + symbol + "\",\"stale\":" + stale + ",\"quote\":{\"symbol\":\"" + symbol
                + "\",\"price\":" + price + ",\"currency\":\"USD\",\"asOf\":\"" + Instant.now() + "\"}}";
    }

    private void fauxnanceAnswers(String... entries) {
        quotesBody = "{\"data\":{\"quotes\":[" + String.join(",", entries) + "]}}";
    }

    private void publishQuote(String symbol, String price) {
        MarketQuote quote = MarketQuote.builder().symbol(symbol).price(new BigDecimal(price)).currency("USD")
                .stale(false).quoteAsOf(Instant.now()).build();
        quoteObservers.forEach(observer -> observer.onQuote(quote));
    }

    private JsonNode notificationsOf(long accountId) throws Exception {
        return call("GET", "/api/v1/accounts/" + accountId + "/notifications", accountId, null).body();
    }

    // ================================================================ access control, end to end

    @Test
    void everyModuleRouteRefusesARequestWithNoToken() throws Exception {
        for (String path : List.of("/api/v1/accounts/1/preferences", "/api/v1/accounts/1/notifications",
                "/api/v1/accounts/1/alerts", "/api/v1/portfolio/1")) {
            Reply reply = call("GET", path, null, null);
            assertEquals(401, reply.status(), path);
            assertEquals("AUTH-401", reply.body().path("errorCode").asText(), path);
        }
    }

    @Test
    void everyModuleRouteRefusesAnotherAccountsTokenWith403() throws Exception {
        for (String path : List.of("/api/v1/accounts/1/preferences", "/api/v1/accounts/1/notifications",
                "/api/v1/accounts/1/alerts", "/api/v1/portfolio/1", "/api/v1/portfolio/1/positions",
                "/api/v1/portfolio/1/pnl")) {
            Reply reply = call("GET", path, 5L, null);
            assertEquals(403, reply.status(), path + " must be 403, not an empty answer or a 404");
            assertEquals("ACC-403", reply.body().path("errorCode").asText(), path);
        }
    }

    // ================================================================ 1. preferences

    @Test
    void preferencesPersistAreScopedAndRefuseADestination() throws Exception {
        Reply defaults = call("GET", "/api/v1/accounts/1/preferences", 1L, null);
        assertEquals(200, defaults.status());
        assertEquals("DEFAULT", defaults.body().path("source").asText());
        assertEquals("EMAIL", defaults.body().path("alertChannel").asText());

        Reply saved = call("PUT", "/api/v1/accounts/1/preferences", 1L, "{\"defaultAccountId\":1,\"alertChannel\":\"SMS\"}");
        assertEquals(200, saved.status(), saved.body().toString());
        assertEquals("SMS", saved.body().path("alertChannel").asText());
        assertEquals("STORED", saved.body().path("source").asText());
        assertEquals("******3210", saved.body().path("contactDetail").asText(), "masked, never the full number");
        assertFalse(saved.body().toString().contains("9876543210"));
        assertFalse(saved.body().toString().contains("john.doe"));

        // survives: it is a row in the database, keyed on the CLIENT, and holds no copy of a contact detail
        assertEquals("SMS", jdbc.queryForObject("SELECT alert_channel FROM customer_preferences WHERE client_id = 1", String.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_name = 'customer_preferences' AND column_name IN ('email', 'phone', 'contact_detail')""",
                Integer.class), "contact details must not be copied into the preferences table");

        // a customer's second account (same client) sees the same preference
        // (the token below only names account 1, so the account-2 route is 403; the row itself is per client)
        assertEquals(403, call("GET", "/api/v1/accounts/2/preferences", 1L, null).status());

        // refused: a destination, a channel outside the set, a default account the token cannot use
        assertEquals(422, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"EMAIL\",\"webhookUrl\":\"http://169.254.169.254/\"}").status());
        assertEquals(422, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"WEBHOOK\"}").status());
        assertEquals(403, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":2,\"alertChannel\":\"EMAIL\"}").status());
    }

    @Test
    void aChannelTheProfileCannotServeIsRefused() throws Exception {
        // client 4 (account 4) has no phone number
        Reply refused = call("PUT", "/api/v1/accounts/4/preferences", 4L, "{\"defaultAccountId\":4,\"alertChannel\":\"SMS\"}");

        assertEquals(422, refused.status());
        assertEquals("VAL-422", refused.body().path("errorCode").asText());
    }

    // ================================================================ 2. notifications on the stored channel, idempotently

    @Test
    void aTradeEventIsDeliveredOnTheStoredChannelAndAReplayChangesNothing() throws Exception {
        call("PUT", "/api/v1/accounts/1/preferences", 1L, "{\"defaultAccountId\":1,\"alertChannel\":\"SMS\"}");
        UUID eventId = UUID.randomUUID();

        tradeEvents.handle(TradeEventListenerTest.filled(eventId));
        tradeEvents.handle(TradeEventListenerTest.filled(eventId));   // the replay
        tradeEvents.handle(TradeEventListenerTest.filled(eventId));   // and again
        dispatcher.dispatchDue();
        dispatcher.dispatchDue();                                       // a second tick must not send it again

        JsonNode inbox = notificationsOf(1L);
        assertEquals(1, inbox.size(), "replayed three times, recorded once: " + inbox);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class));
        assertEquals("SENT", inbox.get(0).path("status").asText());
        assertEquals("SMS", inbox.get(0).path("channel").asText(), "the record shows the channel it went out on");
        assertEquals("STORED", inbox.get(0).path("channelSource").asText());
        assertEquals("ORDER_FILLED", inbox.get(0).path("kind").asText());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM notifications", Integer.class), "sent exactly once");
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE body ~ '[A-Za-z0-9._%+-]+@' OR subject ~ '[0-9]{13,}'", Integer.class));
    }

    @Test
    void changingThePreferenceChangesTheChannelOfTheNextMessage() throws Exception {
        call("PUT", "/api/v1/accounts/1/preferences", 1L, "{\"defaultAccountId\":1,\"alertChannel\":\"SMS\"}");
        tradeEvents.handle(TradeEventListenerTest.filled(UUID.randomUUID()));
        dispatcher.dispatchDue();

        call("PUT", "/api/v1/accounts/1/preferences", 1L, "{\"defaultAccountId\":1,\"alertChannel\":\"EMAIL\"}");
        tradeEvents.handle(TradeEventListenerTest.rejected(UUID.randomUUID(), "INSUFFICIENT_FUNDS"));
        dispatcher.dispatchDue();

        JsonNode inbox = notificationsOf(1L);   // newest first
        assertEquals("EMAIL", inbox.get(0).path("channel").asText());
        assertEquals("ORDER_REJECTED", inbox.get(0).path("kind").asText(), "a rejection is news too");
        assertEquals("SMS", inbox.get(1).path("channel").asText());
    }

    @Test
    void withNoPreferenceTheDocumentedDefaultIsUsedAndRecordedAsTheDefault() throws Exception {
        String event = TradeEventListenerTest.filled(UUID.randomUUID()).replace("\"accountId\":1", "\"accountId\":12");

        tradeEvents.handle(event);
        dispatcher.dispatchDue();

        JsonNode inbox = notificationsOf(12L);
        assertEquals("EMAIL", inbox.get(0).path("channel").asText());
        assertEquals("DEFAULT", inbox.get(0).path("channelSource").asText());
    }

    @Test
    void whenTheChosenChannelHasNoContactTheMessageIsHeldNotMisrouted() throws Exception {
        // client 4 / account 4 (one client, one account) has no phone; a stored SMS preference (written directly, the API refuses it)
        jdbc.update("INSERT INTO customer_preferences (client_id, default_account_id, alert_channel) VALUES (4, 4, 'SMS')");
        tradeEvents.handle(TradeEventListenerTest.filled(UUID.randomUUID()).replace("\"accountId\":1", "\"accountId\":4"));

        dispatcher.dispatchDue();

        JsonNode inbox = notificationsOf(4L);
        assertEquals("HELD", inbox.get(0).path("status").asText());
        assertEquals("NO_CONTACT", inbox.get(0).path("statusReason").asText());
        assertTrue(inbox.get(0).path("channel").isNull());
    }

    @Test
    void notificationHistoryIsOnlyForItsOwner() throws Exception {
        tradeEvents.handle(TradeEventListenerTest.filled(UUID.randomUUID()));
        long id = jdbc.queryForObject("SELECT id FROM notifications", Long.class);

        assertEquals(403, call("GET", "/api/v1/accounts/1/notifications", 5L, null).status());
        Reply theirs = call("POST", "/api/v1/accounts/5/notifications/" + id + "/read", 5L, null);
        assertEquals(404, theirs.status(), "another account's message looks like it does not exist");
        assertEquals("NTF-404", theirs.body().path("errorCode").asText());
        assertEquals(1, call("GET", "/api/v1/accounts/1/notifications/unread-count", 1L, null).body().path("unread").asInt());

        assertEquals(200, call("POST", "/api/v1/accounts/1/notifications/" + id + "/read", 1L, null).status());
        assertEquals(0, call("GET", "/api/v1/accounts/1/notifications/unread-count", 1L, null).body().path("unread").asInt());
    }

    // ================================================================ 3. alerts: crossing, delivery through notifications, cap

    @Test
    void aCrossedAlertIsDeliveredThroughNotificationsOnThePreferredChannelAndFiresOnce() throws Exception {
        call("PUT", "/api/v1/accounts/1/preferences", 1L, "{\"defaultAccountId\":1,\"alertChannel\":\"PUSH\"}");
        Reply created = call("POST", "/api/v1/accounts/1/alerts", 1L,
                "{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":200.00}");
        assertEquals(201, created.status(), created.body().toString());
        long alertId = created.body().path("id").asLong();

        publishQuote("AAPL", "199.00");
        assertEquals("ACTIVE", call("GET", "/api/v1/accounts/1/alerts", 1L, null).body().get(0).path("status").asText());
        publishQuote("AAPL", "201.50");
        publishQuote("AAPL", "205.00");
        publishQuote("AAPL", "210.00");

        JsonNode alert = call("GET", "/api/v1/accounts/1/alerts", 1L, null).body().get(0);
        assertEquals(alertId, alert.path("id").asLong());
        assertEquals("TRIGGERED", alert.path("status").asText());
        assertEquals("HANDED_OVER", alert.path("deliveryState").asText(), "the customer can see it was handed over");
        assertEquals(201.5, alert.path("triggeredPrice").asDouble());

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'PRICE_ALERT'", Integer.class),
                "fired once, not on every quote past the level");
        dispatcher.dispatchDue();
        JsonNode inbox = notificationsOf(1L);
        assertEquals("PRICE_ALERT", inbox.get(0).path("kind").asText());
        assertEquals("PUSH", inbox.get(0).path("channel").asText(), "the alert went out on the stored channel");
        assertTrue(inbox.get(0).path("body").asText().contains("AAPL is at or above 200.00"));
    }

    @Test
    void alertsAreCappedPerAccountAndScopedToTheAccount() throws Exception {
        for (int i = 1; i <= 3; i++) {
            assertEquals(201, call("POST", "/api/v1/accounts/1/alerts", 1L,
                    "{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":" + (300 + i) + "}").status());
        }
        Reply over = call("POST", "/api/v1/accounts/1/alerts", 1L, "{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":400}");
        assertEquals(409, over.status());
        assertEquals("ALR-409", over.body().path("errorCode").asText());

        // another account is not blocked, and cannot see or remove the first account's alerts
        assertEquals(201, call("POST", "/api/v1/accounts/5/alerts", 5L, "{\"symbol\":\"AAPL\",\"condition\":\"<=\",\"price\":1}").status());
        long theirs = jdbc.queryForObject("SELECT id FROM price_alerts WHERE account_id = 1 LIMIT 1", Long.class);
        assertEquals(404, call("DELETE", "/api/v1/accounts/5/alerts/" + theirs, 5L, null).status());
        assertEquals(1, call("GET", "/api/v1/accounts/5/alerts", 5L, null).body().size());
    }


    @Test
    void anAlertCanBeSwitchedOffEditedRearmedAndKeepsOneHistoryRowPerFiring() throws Exception {
        Reply created = call("POST", "/api/v1/accounts/1/alerts", 1L, "{\"symbol\":\"AAPL\",\"condition\":\">\",\"price\":200.00}");
        assertEquals(201, created.status(), created.body().toString());
        long id = created.body().path("id").asLong();
        assertEquals(">", created.body().path("condition").asText());
        assertTrue(created.body().path("enabled").asBoolean());

        // switched off: a quote past the level does nothing
        assertEquals(200, call("PUT", "/api/v1/accounts/1/alerts/" + id + "/enabled", 1L, "{\"enabled\":false}").status());
        publishQuote("AAPL", "210.00");
        assertEquals(0, call("GET", "/api/v1/accounts/1/alerts/" + id + "/triggers", 1L, null).body().path("triggers").size());

        // switched on: it fires, once
        assertEquals(200, call("PUT", "/api/v1/accounts/1/alerts/" + id + "/enabled", 1L, "{\"enabled\":true}").status());
        publishQuote("AAPL", "211.00");
        publishQuote("AAPL", "212.00");
        JsonNode first = call("GET", "/api/v1/accounts/1/alerts/" + id + "/triggers", 1L, null).body();
        assertEquals(1, first.path("triggers").size(), "one firing, one history row");
        assertEquals(211.0, first.path("triggers").get(0).path("triggerPrice").asDouble());
        assertEquals("TRIGGERED", call("GET", "/api/v1/accounts/1/alerts", 1L, null).body().get(0).path("status").asText());

        // an edit arms it again, with the history kept; the second firing is a second row and a second message
        assertEquals(200, call("PUT", "/api/v1/accounts/1/alerts/" + id, 1L, "{\"condition\":\">=\",\"price\":300.00}").status());
        assertEquals("ACTIVE", call("GET", "/api/v1/accounts/1/alerts", 1L, null).body().get(0).path("status").asText());
        publishQuote("AAPL", "301.00");
        assertEquals(2, call("GET", "/api/v1/accounts/1/alerts/" + id + "/triggers", 1L, null).body().path("triggers").size());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE kind = 'PRICE_ALERT'", Integer.class));

        // another account cannot read the history or flip the switch
        assertEquals(404, call("GET", "/api/v1/accounts/5/alerts/" + id + "/triggers", 5L, null).status());
        assertEquals(404, call("PUT", "/api/v1/accounts/5/alerts/" + id + "/enabled", 5L, "{\"enabled\":false}").status());

        // deleting removes the alert and its history
        assertEquals(200, call("DELETE", "/api/v1/accounts/1/alerts/" + id, 1L, null).status());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM price_alert_triggers", Integer.class));
    }

    @Test
    void theInboxCanBeFilteredByTypeAndCounted() throws Exception {
        call("POST", "/api/v1/accounts/1/alerts", 1L, "{\"symbol\":\"AAPL\",\"condition\":\"==\",\"price\":150.00}");
        publishQuote("AAPL", "150.00");

        JsonNode alerts = call("GET", "/api/v1/accounts/1/notifications?type=price_alert", 1L, null).body();
        assertEquals(1, alerts.size());
        assertTrue(alerts.get(0).path("body").asText().contains("AAPL equals 150.00"));
        assertEquals(0, call("GET", "/api/v1/accounts/1/notifications?type=order", 1L, null).body().size());
        assertEquals(1, call("GET", "/api/v1/accounts/1/notifications/counts", 1L, null).body().path("priceAlert").asInt());
        assertEquals(422, call("GET", "/api/v1/accounts/1/notifications?type=bogus", 1L, null).status());
    }


    @Test
    void fundsCanBeAddedAndWithdrawnTheBalanceFollowsAndTheInboxIsTold() throws Exception {
        java.math.BigDecimal seeded = jdbc.queryForObject("SELECT cash_balance FROM accounts WHERE id = 1", java.math.BigDecimal.class);
        try {
            fundsScenario();
        } finally {
            // The other tests read the seeded balance, so leave it as it was found.
            jdbc.update("UPDATE accounts SET cash_balance = ? WHERE id = 1", seeded);
        }
    }

    private void fundsScenario() throws Exception {
        double before = call("GET", "/api/v1/accounts/1/balance", 1L, null).body().path("cashBalance").asDouble();

        Reply added = call("POST", "/api/v1/accounts/1/funds/deposit", 1L, "{\"amount\":250.00,\"method\":\"UPI\"}");
        assertEquals(201, added.status(), added.body().toString());
        assertEquals(before + 250.0, added.body().path("balanceAfter").asDouble(), 0.001);
        assertEquals(200, call("GET", "/api/v1/accounts/1/balance", 1L, null).status());
        assertEquals(before + 250.0, call("GET", "/api/v1/accounts/1/balance", 1L, null).body().path("cashBalance").asDouble(), 0.001);

        assertEquals(201, call("POST", "/api/v1/accounts/1/funds/withdraw", 1L, "{\"amount\":50.00,\"method\":\"BANK_TRANSFER\"}").status());
        assertEquals(before + 200.0, call("GET", "/api/v1/accounts/1/balance", 1L, null).body().path("cashBalance").asDouble(), 0.001);

        // more than the balance, a negative amount and an unknown method are refused; nothing is recorded for them
        assertEquals(422, call("POST", "/api/v1/accounts/1/funds/withdraw", 1L, "{\"amount\":999999.00,\"method\":\"UPI\"}").status());
        assertEquals(422, call("POST", "/api/v1/accounts/1/funds/deposit", 1L, "{\"amount\":-5,\"method\":\"UPI\"}").status());
        assertEquals(422, call("POST", "/api/v1/accounts/1/funds/deposit", 1L, "{\"amount\":5,\"method\":\"CASH\"}").status());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM cash_movements WHERE account_id = 1", Integer.class));
        assertEquals(2, call("GET", "/api/v1/accounts/1/funds", 1L, null).body().size());
        assertEquals(2, call("GET", "/api/v1/accounts/1/notifications?type=funds", 1L, null).body().size());

        // someone else's account is out of reach
        assertEquals(403, call("POST", "/api/v1/accounts/1/funds/deposit", 5L, "{\"amount\":5,\"method\":\"UPI\"}").status());
    }

    @Test
    void theMascotAndNotificationChoicesAreSavedAndAKindSwitchedOffIsNotSent() throws Exception {
        assertEquals("OWL", call("GET", "/api/v1/accounts/1/preferences", 1L, null).body().path("mascot").asText());

        Reply saved = call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"PUSH\",\"mascot\":\"CAT\",\"notificationKinds\":[\"ORDER_FILLED\"]}");
        assertEquals(200, saved.status(), saved.body().toString());
        assertEquals("CAT", saved.body().path("mascot").asText());
        assertEquals(1, saved.body().path("notificationKinds").size());
        assertTrue(saved.body().path("notificationsEnabled").asBoolean());
        assertEquals("CAT", jdbc.queryForObject("SELECT mascot FROM customer_preferences WHERE client_id = 1", String.class));

        assertEquals(422, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"PUSH\",\"notificationKinds\":[\"NOT_A_KIND\"]}").status());
        assertEquals(422, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"PUSH\",\"mascot\":\"DRAGON\"}").status());

        // a price alert fires, but the customer only wants order messages: it is kept and marked skipped
        call("POST", "/api/v1/accounts/1/alerts", 1L, "{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":200.00}");
        publishQuote("AAPL", "201.00");
        dispatcher.dispatchDue();
        JsonNode inbox = call("GET", "/api/v1/accounts/1/notifications?type=price_alert", 1L, null).body();
        assertEquals(1, inbox.size());
        assertEquals("SKIPPED", inbox.get(0).path("status").asText());

        // the master switch silences everything
        assertEquals(200, call("PUT", "/api/v1/accounts/1/preferences", 1L,
                "{\"defaultAccountId\":1,\"alertChannel\":\"PUSH\",\"notificationsEnabled\":false}").status());
        assertFalse(call("GET", "/api/v1/accounts/1/preferences", 1L, null).body().path("notificationsEnabled").asBoolean());
    }

    @Test
    void theValueHistoryQueriesRunAndAPointIsRecordedOnlyOncePerWindow() {
        jdbc.update("TRUNCATE portfolio_value_snapshots");

        assertEquals(1, snapshotMapper.recordIfDue(1L, new java.math.BigDecimal("1000.00"), "USD"));
        assertEquals(0, snapshotMapper.recordIfDue(1L, new java.math.BigDecimal("1001.00"), "USD"), "one point per ten minutes");

        assertEquals(1, snapshotMapper.findSince(1L, java.time.Instant.now().minusSeconds(3600)).size());
        assertEquals(0, snapshotMapper.findSince(2L, java.time.Instant.now().minusSeconds(3600)).size(), "another account has none");
        assertEquals(null, snapshotMapper.findAtOrBefore(1L, java.time.Instant.now().minusSeconds(86400)));
        assertEquals(0, snapshotMapper.netFundsSince(1L, java.time.Instant.now().minusSeconds(86400)).signum());
    }

    @Test
    void everyAlertQueryRunsOnARealDatabase() {
        // The unit tests use an in-memory mapper, so only this proves the SQL itself. The pending hand-over
        // query once lost the space before FROM (a text block strips it) and only failed when its timer fired.
        jdbc.update("INSERT INTO price_alerts (account_id, symbol, direction, operator, threshold, status, delivery_state, triggered_price, triggered_at) "
                + "VALUES (1, 'AAPL', 'ABOVE', 'GTE', 200, 'TRIGGERED', 'PENDING_HANDOVER', 201, CURRENT_TIMESTAMP - interval '60 seconds')");
        jdbc.update("INSERT INTO price_alerts (account_id, symbol, direction, operator, threshold) VALUES (1, 'MSFT', 'BELOW', 'LTE', 100)");

        assertEquals(1, alertMapper.findPendingHandover(5, 10).size(), "the fired alert awaiting hand-over");
        assertEquals(1, alertMapper.findAllActive().size());
        assertEquals(2, alertMapper.findByAccount(1L).size());
        assertEquals(0, alertMapper.findPendingHandover(0, 10).size(), "attempt limit respected");
    }

    @Test
    void anAlertOnAnUnknownSymbolOrWithABadThresholdIsRefused() throws Exception {
        assertEquals(404, call("POST", "/api/v1/accounts/1/alerts", 1L,
                "{\"symbol\":\"NOSUCH\",\"condition\":\">=\",\"price\":5}").status());
        assertEquals(422, call("POST", "/api/v1/accounts/1/alerts", 1L,
                "{\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":-5}").status());
        assertEquals(422, call("POST", "/api/v1/accounts/1/alerts", 1L,
                "{\"accountId\":2,\"symbol\":\"AAPL\",\"condition\":\">=\",\"price\":5}").status());
    }

    // ================================================================ 4. portfolio

    @Test
    void thePortfolioIsPricedFromOneBatchedCallAndCachedWithinTheWindow() throws Exception {
        fauxnanceAnswers(quoteFor("AAPL", "232.71", false), quoteFor("MSFT", "400.00", false));

        Reply summary = call("GET", "/api/v1/portfolio/1", 1L, null);

        assertEquals(200, summary.status(), summary.body().toString());
        JsonNode s = summary.body();
        assertEquals("USD", s.path("baseCurrency").asText());
        assertEquals(15000.50, s.path("cashBalance").asDouble(), 0.001);
        assertEquals(19635.50, s.path("marketValue").asDouble(), 0.001, "50 x 232.71 + 20 x 400.00");
        assertEquals(14714.50, s.path("costBasis").asDouble(), 0.001, "50 x 170.25 + 20 x 310.10");
        assertEquals(4921.00, s.path("unrealisedPnl").asDouble(), 0.001);
        assertEquals(34636.00, s.path("totalValue").asDouble(), 0.001, "cash + market value");
        assertEquals(2, s.path("positionCount").asInt());
        assertFalse(s.path("partial").asBoolean());
        assertEquals(1, quoteCalls.get(), "two holdings, ONE call to Fauxnance");

        call("GET", "/api/v1/portfolio/1", 1L, null);
        call("GET", "/api/v1/portfolio/1/positions", 1L, null);
        assertEquals(1, quoteCalls.get(), "a cached quote is reused within the window");

        JsonNode positions = call("GET", "/api/v1/portfolio/1/positions", 1L, null).body();
        assertEquals("AAPL", positions.get(0).path("symbol").asText());
        assertEquals(232.71, positions.get(0).path("lastPrice").asDouble(), 0.0001);
        assertFalse(positions.get(0).path("priceAsOf").isNull(), "every priced figure says when it was observed");
        assertFalse(positions.get(0).path("stale").asBoolean());
    }

    @Test
    void whenOnlySomeSymbolsCanBePricedTheAnswerIsPartialAndTheRestIsMarkedStale() throws Exception {
        // account 5 holds AAPL and TSLA; Fauxnance answers for AAPL only
        fauxnanceAnswers(quoteFor("AAPL", "170.00", false));

        Reply summary = call("GET", "/api/v1/portfolio/5", 5L, null);
        JsonNode positions = call("GET", "/api/v1/portfolio/5/positions", 5L, null).body();

        assertEquals(200, summary.status());
        assertTrue(summary.body().path("partial").asBoolean());
        assertEquals(2, summary.body().path("positionCount").asInt());
        JsonNode tsla = positions.get(0).path("symbol").asText().equals("TSLA") ? positions.get(0) : positions.get(1);
        assertTrue(tsla.path("lastPrice").isNull());
        assertTrue(tsla.path("stale").asBoolean());
        assertEquals(7500.00, tsla.path("costBasis").asDouble(), 0.001, "cost basis is known without a price");
    }

    @Test
    void whenNothingCanBePricedTheAnswerIsTheDocumentedServiceUnavailable() throws Exception {
        // account 12 holds MSFT only; Fauxnance answers with nothing and there is no earlier price
        Reply reply = call("GET", "/api/v1/portfolio/12", 12L, null);

        assertEquals(503, reply.status());
        assertEquals("MKT-503", reply.body().path("errorCode").asText());
        assertEquals("Pricing unavailable", reply.body().path("message").asText());
        // and order placement is unaffected by a pricing outage: the order route is not even touched
        assertEquals(200, call("GET", "/api/v1/accounts/12/positions", 12L, null).status());
    }

    @Test
    void holdingsAreConvertedIntoTheAccountsBaseCurrency() throws Exception {
        // account 3 is a EUR account holding FX:EURUSD and FX:GBPUSD, both priced in USD
        fauxnanceAnswers(quoteFor("FX:EURUSD", "1.10", false), quoteFor("FX:GBPUSD", "1.30", false));

        JsonNode s = call("GET", "/api/v1/portfolio/3", 3L, null).body();

        assertEquals("EUR", s.path("baseCurrency").asText());
        // 10000 x 1.10 + 5000 x 1.30 = 17500 USD = 14875.00 EUR at the platform's 0.85
        assertEquals(14875.00, s.path("marketValue").asDouble(), 0.01);
        assertFalse(s.path("partial").asBoolean());
    }

    @Test
    void realisedProfitIsBookedOnceAtTheSaleAndDoesNotMoveWithThePrice() throws Exception {
        fauxnanceAnswers(quoteFor("AAPL", "232.71", false), quoteFor("MSFT", "400.00", false));
        UUID sale = UUID.randomUUID();

        realisedBook.bookSale(sale, 1L, "AAPL", 10, new BigDecimal("180.00"), new BigDecimal("170.25"),
                Instant.parse("2026-10-05T10:00:00Z"));
        realisedBook.bookSale(sale, 1L, "AAPL", 10, new BigDecimal("180.00"), new BigDecimal("170.25"),
                Instant.parse("2026-10-05T10:00:00Z"));   // a replayed event

        JsonNode pnl = call("GET", "/api/v1/portfolio/1/pnl?bySymbol=true", 1L, null).body();
        assertEquals(97.50, pnl.path("realisedPnl").asDouble(), 0.001, "(180.00 - 170.25) x 10, once");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM portfolio_realised_pnl", Integer.class));
        assertEquals(97.50, pnl.path("bySymbol").get(0).path("realisedPnl").asDouble(), 0.001);

        // a different price now changes the unrealised figure and leaves the realised one alone
        quotesBody = "{\"data\":{\"quotes\":[" + quoteFor("AAPL", "999.00", false) + "," + quoteFor("MSFT", "1.00", false) + "]}}";
        jdbc.update("UPDATE portfolio_realised_pnl SET booked_at = booked_at");   // no-op, proves nothing recomputes
        assertEquals(97.50, call("GET", "/api/v1/portfolio/1/pnl", 1L, null).body().path("realisedPnl").asDouble(), 0.001);

        // the window bounds the realised figure only
        assertEquals(97.50, call("GET", "/api/v1/portfolio/1/pnl?from=2026-10-01&to=2026-10-31", 1L, null).body()
                .path("realisedPnl").asDouble(), 0.001);
        assertEquals(0.0, call("GET", "/api/v1/portfolio/1/pnl?from=2026-09-01&to=2026-09-30", 1L, null).body()
                .path("realisedPnl").asDouble(), 0.001);
        assertEquals(422, call("GET", "/api/v1/portfolio/1/pnl?from=2026-10-31&to=2026-10-01", 1L, null).status());
        assertEquals(422, call("GET", "/api/v1/portfolio/1/pnl?from=garbage", 1L, null).status());
    }

    @Test
    void theHealthCheckIsPublicAndReportsItsDependencies() throws Exception {
        Reply health = call("GET", "/health", null, null);

        assertEquals(200, health.status());
        assertEquals("postgres", health.body().path("dependencies").get(0).path("name").asText());
        assertEquals("ok", health.body().path("dependencies").get(0).path("status").asText());
        assertEquals("fauxnance", health.body().path("dependencies").get(1).path("name").asText());
        assertEquals(1873, health.body().path("dependencies").get(1).path("quotaRemaining").asInt());
        assertFalse(health.body().toString().contains("integration-key"), "the Fauxnance key never leaves the server");
    }

    @Test
    void theSprintSixRoutesStillAnswerExactlyAsBefore() throws Exception {
        assertEquals(200, call("GET", "/api/v1/accounts/1", 1L, null).status());
        assertEquals(200, call("GET", "/api/v1/accounts/1/balance", 1L, null).status());
        assertEquals(2, call("GET", "/api/v1/accounts/1/positions", 1L, null).body().size());
        assertEquals(200, call("GET", "/api/v1/accounts/1/orders", 1L, null).status());
        assertEquals(200, call("GET", "/api/v1/accounts/1/watchlist", 1L, null).status());
        assertEquals(403, call("GET", "/api/v1/accounts/1/balance", 5L, null).status());
        assertEquals("ACC-403", call("GET", "/api/v1/accounts/1/balance", 5L, null).body().path("errorCode").asText());
    }
}
