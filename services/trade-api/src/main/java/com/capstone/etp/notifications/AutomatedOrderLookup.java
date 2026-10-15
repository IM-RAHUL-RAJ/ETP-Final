package com.capstone.etp.notifications;

import java.util.Optional;
import java.util.UUID;

/**
 * Was this order placed by an automated strategy? If so, a few words naming the strategy ("your moving-average crossover
 * strategy #5 on AAPL"), so the customer's message says plainly that the trade was automated.
 */
public interface AutomatedOrderLookup {

    Optional<String> describe(UUID orderId);
}
