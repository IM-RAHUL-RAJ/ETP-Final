package com.capstone.etp.automation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.automation.*. The strategies themselves are the customers' own, kept in the database and switched on
 * from the app. {@code enabled} is the platform-wide kill switch: set it to false and nothing trades, whatever
 * customers have switched on.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.automation")
public class AutomationProperties {

    /** The kill switch. */
    private boolean enabled = true;
    /** Where the Trade REST API is: this same service, built from ORDER_SERVICE_URL and ORDER_SERVICE_PORT. */
    private String tradeApiBaseUrl;
    private int connectTimeoutMs = 2000;
    private int readTimeoutMs = 4000;
    /** Prices kept per symbol. Must cover the longest period any strategy uses, plus one. */
    private int historySize = 500;
    /**
     * Allows the demo button: a scripted price path that makes a running strategy trigger now. The order it produces is real,
     * so it is off unless asked for (AUTOMATION_SIMULATION=true), and meant for demonstrations and testing.
     */
    private boolean simulationEnabled = false;
    /**
     * Allowance on an automated order's limit, in percent: a BUY is sent at the ask plus this, a SELL at the bid minus this, so a
     * quote that moves a little before the executor checks it does not get the order refused.
     */
    private java.math.BigDecimal limitBufferPercent = new java.math.BigDecimal("0.10");
    /** Strategies one account may hold, on or off. */
    private int maxPerAccount = 10;
}
