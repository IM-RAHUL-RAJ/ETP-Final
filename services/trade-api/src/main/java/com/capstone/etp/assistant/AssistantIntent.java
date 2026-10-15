package com.capstone.etp.assistant;

/** What the customer is asking about. ADVISORY, BLOCKED and OFF_TOPIC are refusals; the rest are answered from the account's own data. */
public enum AssistantIntent {
    PORTFOLIO_PERFORMANCE,
    TOP_MOVERS,
    RECENT_TRANSACTIONS,
    BUY_SELL_COUNT,
    OPEN_ORDERS,
    STALE_PRICES,
    PNL_SUMMARY,
    ACTIVITY_TREND,
    POSITIONS,
    ALERTS,
    /** Something about the app or the account that has no dedicated answer: the overview is given to the model. */
    GENERAL,
    /** Hello, thanks, goodbye, "what can you do": answered with a welcome and the questions on offer. */
    GREETING,
    /** Investment advice or a recommendation: refused, with a pointer to the Advice page. */
    ADVISORY,
    /** An attempt to change the rules, or to get at the prompt, keys or tokens. */
    BLOCKED,
    /** Nothing to do with the app. */
    OFF_TOPIC
}
