package com.capstone.etp.executor.poller;

import com.capstone.etp.executor.client.FauxnanceClient;
import com.capstone.etp.executor.exception.RateLimitException;
import com.capstone.etp.executor.exception.SymbolRequestException;
import com.capstone.etp.executor.exception.TransientNetworkException;
import com.capstone.etp.executor.kafka.models.KafkaMessageEnvelope;
import com.capstone.etp.executor.kafka.models.QuotePayload;
import com.capstone.etp.executor.mapper.ExecutorOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataPoller {

    private static final int MAX_BATCH_SIZE = 25;
    private static final int DAILY_REQUEST_QUOTA = 2000;
    private static final int SECONDS_PER_DAY = 86_400;
    private static final int MIN_INTERVAL_FLOOR_SECONDS = 45;
    private static final String EVENT_TYPE  = "QUOTE";
    private static final String SOURCE      = "trade-executor";
    private static final int    SCHEMA_VER  = 1;

    private final ExecutorOrderMapper mapper;
    private final FauxnanceClient fauxnanceClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.market-data:market-data}")
    private String marketDataTopic;

    @Value("${app.poller.interval-seconds:60}")
    private int pollIntervalSeconds;

    @Scheduled(fixedDelayString = "#{${app.poller.interval-seconds:60} * 1000}")
    public void poll() {
        List<String> symbols = mapper.findSymbolsForMarketDataPoller();
        if (symbols.isEmpty()) {
            log.debug("No watchlist/holding/new-order symbols found - skipping poll");
            return;
        }

        if (!isIntervalWithinDailyQuota(pollIntervalSeconds, symbols.size())) {
            int requiredInterval = requiredIntervalSeconds(symbols.size());
            int requestsPerDay = requestsPerDay(symbols.size(), pollIntervalSeconds);
            log.error("Skipping market-data poll. intervalSeconds={} requiredMinSeconds={} symbols={} requestsPerDay={} quotaPerDay={}",
                    pollIntervalSeconds, requiredInterval, symbols.size(), requestsPerDay, DAILY_REQUEST_QUOTA);
            return;
        }

        log.debug("Polling {} symbol(s) from Fauxnance", symbols.size());

        // Chunk into batches of at most MAX_BATCH_SIZE
        for (int start = 0; start < symbols.size(); start += MAX_BATCH_SIZE) {
            int end   = Math.min(start + MAX_BATCH_SIZE, symbols.size());
            List<String> batch = symbols.subList(start, end);

            try {
                log.info("===============================\n\n Fetching batch of " + batch.size() + " symbols: " + batch + " \n\n ===============================");
                List<QuotePayload> quotes = fauxnanceClient.getBatchQuotes(batch);
                for (QuotePayload quote : quotes) {
                    publishQuote(quote);
                }
            } catch (RateLimitException e) {
                log.error("Rate limited by Fauxnance for batch {}. retryAfterSeconds={}. Ending this poll cycle.",
                        batch, e.getRetryAfterSeconds());
                return;
            } catch (SymbolRequestException e) {
                log.error("Batch request rejected for symbolSet='{}' status={} body={}",
                        e.getSymbol(), e.getStatusCode(), trimBody(e.getBody()));
            } catch (TransientNetworkException e) {
                log.warn("Transient Fauxnance failure for symbolSet='{}' attempts={} cause={}",
                        e.getSymbol(), e.getAttempts(), e.getMessage());
            }
        }
    }

    static boolean isIntervalWithinDailyQuota(int intervalSeconds, int symbolCount) {
        if (intervalSeconds <= 0 || symbolCount <= 0) {
            return false;
        }
        return intervalSeconds >= requiredIntervalSeconds(symbolCount);
    }

    static int requiredIntervalSeconds(int symbolCount) {
        int requestsPerPoll = batchCount(symbolCount);
        int quotaFloor = ceilDiv(SECONDS_PER_DAY * requestsPerPoll, DAILY_REQUEST_QUOTA);
        return Math.max(MIN_INTERVAL_FLOOR_SECONDS, quotaFloor);
    }

    static int requestsPerDay(int symbolCount, int intervalSeconds) {
        int requestsPerPoll = batchCount(symbolCount);
        int pollsPerDay = ceilDiv(SECONDS_PER_DAY, intervalSeconds);
        return pollsPerDay * requestsPerPoll;
    }

    private static int batchCount(int symbolCount) {
        return ceilDiv(symbolCount, MAX_BATCH_SIZE);
    }

    private static int ceilDiv(int x, int y) {
        return (x + y - 1) / y;
    }

    private void publishQuote(QuotePayload quote) {
        if (quote == null || quote.getSymbol() == null || quote.getSymbol().isBlank()) {
            log.warn("Skipping quote publish because symbol is missing");
            return;
        }
        KafkaMessageEnvelope<QuotePayload> envelope = new KafkaMessageEnvelope<>(
                UUID.randomUUID(), EVENT_TYPE, Instant.now(), SOURCE, SCHEMA_VER, quote);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(marketDataTopic, quote.getSymbol(), json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise quote for {}: {}", quote.getSymbol(), e.getMessage());
        }
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.strip();
        return trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
    }
}

