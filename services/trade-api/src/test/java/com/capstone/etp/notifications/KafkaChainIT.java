package com.capstone.etp.notifications;

import com.capstone.etp.TradeApiApplication;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.kafka.test.utils.ContainerTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@EmbeddedKafka(partitions = 1, topics = {"trade-events", "trade-events.DLT", "market-data"})
@SpringBootTest(classes = TradeApiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.auth.jwt.secret=integration-test-secret-integration-test-secret",
                "app.notifications.dispatch-interval-ms=500",
                "app.alerts.handover-retry-ms=3600000",
                "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
                "app.fauxnance.api-key=none"
        })
class KafkaChainIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "postgres"));
    }

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    KafkaListenerEndpointRegistry registry;
    @Autowired
    EmbeddedKafkaBroker broker;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE notifications, price_alerts, portfolio_realised_pnl, customer_preferences RESTART IDENTITY");
        for (MessageListenerContainer container : registry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
    }

    private static String filled(UUID eventId, String side, String extra) {
        return """
                {"eventId":"%s","eventType":"ORDER_FILLED","eventTime":"2026-10-06T09:14:24Z","source":"trade-executor","schemaVersion":1,
                 "payload":{"orderId":"6f2b1c2a-6a1e-4a4f-9c0d-2f7a1b3c4d5e","accountId":1,"symbol":"AAPL","side":"%s","quantity":10,
                 "price":233.00,"executedPrice":180.00,"status":"FILLED","reason":null,"cashDelta":1800.00,"positionQuantityAfter":40,
                 "averageCostAfter":170.25,"executedOn":"2026-10-06T09:14:24Z"%s}}""".formatted(eventId, side, extra);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void theListenersUseOneGroupEachAndNoGroupIsShared() {
        Set<String> groups = registry.getListenerContainers().stream()
                .map(MessageListenerContainer::getGroupId).collect(Collectors.toSet());

        assertEquals(Set.of("watchlist-service", "notification-service", "portfolio-service"), groups);
        assertEquals(3, registry.getListenerContainers().size(), "three listeners, three distinct groups");
    }

    @Test
    void aReplayedTradeEventIsRecordedAndSentOnceAndTheOffsetIsCommittedAfterwards() throws Exception {
        jdbc.update("INSERT INTO customer_preferences (client_id, default_account_id, alert_channel) VALUES (1, 1, 'SMS')");
        UUID eventId = UUID.randomUUID();

        kafka.send("trade-events", "1", filled(eventId, "BUY", "")).get();
        kafka.send("trade-events", "1", filled(eventId, "BUY", "")).get();   // the replay
        kafka.send("trade-events", "1", filled(eventId, "BUY", "")).get();   // and another

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals("SENT", jdbc.queryForObject("SELECT status FROM notifications", String.class)));
        Thread.sleep(3_000);   // give the duplicates every chance to do harm
        assertEquals(1, count("SELECT COUNT(*) FROM notifications"), "three deliveries, one message");
        assertEquals(1, count("SELECT attempts FROM notifications"), "sent exactly once");
        assertEquals("SMS", jdbc.queryForObject("SELECT resolved_channel FROM notifications", String.class));

        // the consumer group's offset moved past the three records: committed after they were handled
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", broker.getBrokersAsString()))) {
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets("notification-service")
                    .partitionsToOffsetAndMetadata().get();
            long offset = committed.get(new TopicPartition("trade-events", 0)).offset();
            assertTrue(offset >= 3, "committed offset " + offset);
        }
    }

    @Test
    void aPoisonMessageGoesToTheDeadLetterTopicAndDoesNotBlockTheMessagesBehindIt() throws Exception {
        jdbc.update("INSERT INTO customer_preferences (client_id, default_account_id, alert_channel) VALUES (1, 1, 'PUSH')");

        kafka.send("trade-events", "1", "{this is not json").get();
        kafka.send("trade-events", "1", filled(UUID.randomUUID(), "BUY", "")).get();   // behind the poison message

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals(1, count("SELECT COUNT(*) FROM notifications"), "the valid event was still processed"));

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-inspector-" + UUID.randomUUID(), "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> inspector = new DefaultKafkaConsumerFactory<>(props,
                new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(inspector, "trade-events.DLT");
            // Two groups read trade-events (notifications and portfolio). Each one dead-letters the message
            // on its own, so it is on the dead-letter topic once per group, and the original travels with it.
            Set<String> deadLetteredBy = new java.util.HashSet<>();
            long deadline = System.currentTimeMillis() + 30_000;
            while (deadLetteredBy.size() < 2 && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> dead : KafkaTestUtils.getRecords(inspector, Duration.ofSeconds(2))) {
                    assertEquals("{this is not json", dead.value(), "the original message is kept for inspection");
                    assertTrue(dead.headers().lastHeader("kafka_dlt-exception-fqcn") != null, "the reason travels with it");
                    deadLetteredBy.add(new String(dead.headers().lastHeader("kafka_dlt-original-consumer-group").value()));
                }
            }
            assertEquals(Set.of("notification-service", "portfolio-service"), deadLetteredBy);
        }
    }

    @Test
    void aSaleOnTheTopicIsBookedOnceEvenWhenReplayed() throws Exception {
        UUID sale = UUID.randomUUID();

        kafka.send("trade-events", "1", filled(sale, "SELL", "")).get();
        kafka.send("trade-events", "1", filled(sale, "SELL", "")).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals(1, count("SELECT COUNT(*) FROM portfolio_realised_pnl")));
        Thread.sleep(2_000);
        assertEquals(1, count("SELECT COUNT(*) FROM portfolio_realised_pnl"));
        assertEquals(0, new java.math.BigDecimal("97.5000").compareTo(
                jdbc.queryForObject("SELECT realised_pnl FROM portfolio_realised_pnl", java.math.BigDecimal.class)),
                "(180.00 - 170.25) x 10");
        // and the same event also became a notification for the customer, through its own group
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals(1, count("SELECT COUNT(*) FROM notifications")));
    }

    @Test
    void aQuoteOnMarketDataTriggersAnAlertThatIsDeliveredThroughNotifications() throws Exception {
        jdbc.update("INSERT INTO customer_preferences (client_id, default_account_id, alert_channel) VALUES (1, 1, 'PUSH')");
        long alertId = jdbc.queryForObject(
                "INSERT INTO price_alerts (account_id, symbol, direction, threshold) VALUES (1, 'AAPL', 'ABOVE', 200) RETURNING id",
                Long.class);
        // the alert module rebuilds its index on a timer; trigger a rebuild the way the timer does
        ReflectionTestUtils.invokeMethod(alertIndex(), "refresh");

        String quote = """
                {"eventId":"%s","eventType":"QUOTE","eventTime":"%s","source":"market-poller","schemaVersion":1,
                 "payload":{"symbol":"AAPL","price":201.25,"bid":201.2,"ask":201.3,"currency":"USD","stale":false,"quoteAsOf":"%s"}}"""
                .formatted(UUID.randomUUID(), Instant.now(), Instant.now());
        kafka.send("market-data", "AAPL", quote).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertEquals("TRIGGERED", jdbc.queryForObject("SELECT status FROM price_alerts WHERE id = " + alertId, String.class));
            assertEquals("HANDED_OVER", jdbc.queryForObject("SELECT delivery_state FROM price_alerts WHERE id = " + alertId, String.class));
        });
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals("SENT", jdbc.queryForObject("SELECT status FROM notifications WHERE kind = 'PRICE_ALERT'", String.class)));
        assertEquals("PUSH", jdbc.queryForObject("SELECT resolved_channel FROM notifications WHERE kind = 'PRICE_ALERT'", String.class));
    }

    @Autowired
    org.springframework.context.ApplicationContext context;

    /** AlertIndex is package-private to the alerts module, so it is looked up by name. */
    private Object alertIndex() throws ClassNotFoundException {
        return context.getBean(Class.forName("com.capstone.etp.alerts.AlertIndex"));
    }
}
