package com.capstone.etp.assistant;

import java.util.List;

/** A question the app offers as a button. */
public record QuickAction(String id, String label) {

    public static final List<QuickAction> ALL = List.of(
            new QuickAction("portfolio_performance", "What is my portfolio performance today and all-time?"),
            new QuickAction("top_movers", "Show my top 5 gainers and losers in current holdings"),
            new QuickAction("last_transactions", "Show my last 10 transactions"),
            new QuickAction("buy_sell_week", "How many BUY vs SELL orders did I place this week?"),
            new QuickAction("open_orders", "Which orders are still NEW or recently REJECTED?"),
            new QuickAction("stale_prices", "Which positions are currently stale-priced or unpriced?"),
            new QuickAction("pnl_summary", "Summarize realised vs unrealised PnL for my account"),
            new QuickAction("activity_trend", "Show activity trend over last 30 days based on my orders"));
}
