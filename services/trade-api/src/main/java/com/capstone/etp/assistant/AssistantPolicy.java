package com.capstone.etp.assistant;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic rules that run before any model is called: is this advice, an attempt to subvert the assistant,
 * off topic, or a question about the customer's own data (and which). Nothing here depends on the model behaving.
 */
public final class AssistantPolicy {

    public static final String ADVISORY_REFUSAL = "I cannot provide investment advice. Please use the Advice page for research signals.";
    public static final String BLOCKED_REFUSAL = "I can only help with your own account data in this app. I can't change my instructions or share internal details.";
    public static final String OFF_TOPIC_REFUSAL = "I can only help with your orders, positions, holdings, profit and loss, alerts and account activity. Try one of the questions below.";
    public static final String GREETING_REPLY = "Hi! I am your Quorum companion. I can tell you about your orders, positions, profit and loss, alerts and recent activity. What would you like to know? Pick a question below or type your own.";
    public static final String HELP_REPLY = "I can answer questions about your own account: your recent transactions, portfolio performance, gainers and losers, working or rejected orders, stale prices, realised and unrealised profit and loss, and your activity over time. I cannot place orders or give investment advice. What would you like to know?";
    public static final String ADVICE_ROUTE = "/advice";

    /** Quick-action ids the app offers, and the question each stands for. */
    public static final Map<String, AssistantIntent> QUICK_ACTIONS = Map.of(
            "portfolio_performance", AssistantIntent.PORTFOLIO_PERFORMANCE,
            "top_movers", AssistantIntent.TOP_MOVERS,
            "last_transactions", AssistantIntent.RECENT_TRANSACTIONS,
            "buy_sell_week", AssistantIntent.BUY_SELL_COUNT,
            "open_orders", AssistantIntent.OPEN_ORDERS,
            "stale_prices", AssistantIntent.STALE_PRICES,
            "pnl_summary", AssistantIntent.PNL_SUMMARY,
            "activity_trend", AssistantIntent.ACTIVITY_TREND);

    private static final Pattern GREETING = Pattern.compile(
            "^(hi+|hey+|hello+|hullo|hola|howdy|yo|sup|namaste|good (morning|afternoon|evening|day)|greetings|hi there|hey there|hello there)( there| again| friend| buddy| mate| companion| owl)?[!. ,?]*$");
    private static final Pattern THANKS = Pattern.compile("^(thanks?|thank you|thx|ty|cheers|great|cool|nice|ok|okay|awesome|perfect)( a lot| so much| very much)?( you| mate| buddy)?[!. ,?]*$");
    private static final Pattern BYE = Pattern.compile("^(bye|goodbye|see you|see ya|cya|good night)( later| soon)?[!. ,?]*$");
    private static final Pattern HELP = Pattern.compile("^(help|help me|what can you do|what do you do|who are you|what are you|how does this work|how do you work|menu|options)[!. ,?]*$");

    private static final List<Pattern> ADVISORY = compile(
            "\\bwhat (should|shall|can|do) i (buy|sell|invest|trade|do with|put)\\b",
            "\\bshould i (buy|sell|hold|invest|short|dump|exit)\\b",
            "\\b(stock|share|investment|trading|market|crypto)s? (tip|tips|pick|picks|idea|ideas|advice|recommendation|recommendations|suggestion|suggestions)\\b",
            "\\brecommend(ation|ations|ed)?\\b.*\\b(stock|share|coin|etf|fund|crypto|buy|sell|invest|trade)",
            "\\b(stock|share|coin|etf|fund|crypto)s? (should|to) (i )?(buy|sell|invest)\\b",
            "\\bwhich (stock|share|coin|etf|fund|crypto|company)s?\\b.*\\b(should|to|can) (i )?(buy|sell|invest|pick)\\b",
            "\\bwhat to (buy|sell|invest)\\b",
            "\\b(buy|sell) or (sell|buy|hold)\\b",
            "\\bgive me .*\\b(tip|tips|advice|pick|picks|recommendation|recommendations)\\b",
            "\\bwhere (should|do) i (put|invest|park)\\b");

    private static final List<Pattern> INJECTION = compile(
            "\\bignore (all |any |the |your |my )?(previous |prior |above |earlier )?(instructions|rules|prompt|guidelines|constraints)\\b",
            "\\b(disregard|forget|override|bypass) (all |any |the |your )?(previous |prior |above |earlier )?(instructions|rules|prompt|guidelines|constraints|restrictions)\\b",
            "\\bsystem (prompt|message|instruction)",
            "\\b(reveal|show|print|repeat|leak|tell me|output|dump) (me )?(your |the )?(hidden |secret |initial |original )?(prompt|instructions|rules|configuration|config)\\b",
            "\\b(your|the) (api[ _-]?key|secret key|access token|bearer token|jwt)\\b",
            "\\b(you are now|from now on you|pretend (to be|you)|developer mode|jailbreak|dan mode)\\b",
            "<\\s*/?\\s*(system|user_question|data)\\s*>");

    private static final List<Pattern> APP_WORDS = compile(
            "\\b(order|orders|trade|trades|trading|transaction|transactions|position|positions|holding|holdings|portfolio|pnl|p&l|profit|loss|losses|gain|gains|gainers|losers|"
                    + "balance|cash|alert|alerts|instrument|instruments|watchlist|account|price|prices|priced|stale|fund|funds|deposit|withdraw|withdrawal|"
                    + "buy|sell|bought|sold|filled|rejected|cancelled|canceled|pending|activity|performance|realised|realized|unrealised|unrealized|invested|investment|investments|value|worth|net worth|"
                    + "history|week|month|today|yesterday|stock|stocks|share|shares|money|spent|spend|total|recent|latest|last|my|mine|how much|how many|market|dividend|fee|fees|quote|quotes|symbol|symbols|currency|usd|inr|eur|exposure|diversif\\w*)\\b");

    private AssistantPolicy() {
    }

    /** Lower case, accents and zero-width characters gone, whitespace collapsed: so tricks with spacing and case do not matter. */
    static String normalise(String message) {
        String text = message == null ? "" : message;
        text = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cf}\\p{Cc}&&[^\\n\\t]]", "")
                .toLowerCase(Locale.ROOT);
        return text.replaceAll("\\s+", " ").trim();
    }

    public static boolean isInjection(String message) {
        return matchesAny(INJECTION, normalise(message));
    }

    public static boolean isAdvisory(String message) {
        return matchesAny(ADVISORY, normalise(message));
    }

    /**
     * Which question this is. A chosen quick action decides it outright (the id must be one we offered); otherwise the
     * words do. Anything without an app word in it is off topic.
     */
    public static AssistantIntent classify(String message, String quickAction) {
        if (quickAction != null && QUICK_ACTIONS.containsKey(quickAction)) {
            return QUICK_ACTIONS.get(quickAction);
        }
        String text = normalise(message);
        if (smallTalkReply(message) != null) {
            return AssistantIntent.GREETING;
        }
        if (matches(text, "\\b(transaction|transactions|recent orders|order history|my orders|last \\d+ (orders|trades|transactions)|latest (orders|trades|transactions))\\b")) {
            return AssistantIntent.RECENT_TRANSACTIONS;
        }
        if (matches(text, "\\bhow many (buy|sell)\\b|\\b(buy|sell)s? (vs\\.?|versus|and|against) (buy|sell)|\\b(buy|sell) orders\\b.*\\b(count|how many|week|month|today)\\b")) {
            return AssistantIntent.BUY_SELL_COUNT;
        }
        if (matches(text, "\\b(new|working|open|pending|rejected|cancelled|canceled)\\b.*\\borders?\\b|\\borders?\\b.*\\b(still|new|rejected|pending|open)\\b")) {
            return AssistantIntent.OPEN_ORDERS;
        }
        if (matches(text, "\\b(gainers?|losers?|top \\d|top movers?|biggest (gain|loss|winner|loser)|best performing|worst performing)\\b")) {
            return AssistantIntent.TOP_MOVERS;
        }
        if (matches(text, "\\b(stale|unpriced|delayed|out of date|missing price|no price)\\b")) {
            return AssistantIntent.STALE_PRICES;
        }
        if (matches(text, "\\b(pnl|p&l|profit and loss|profit|loss|realised|realized|unrealised|unrealized)\\b")) {
            return AssistantIntent.PNL_SUMMARY;
        }
        if (matches(text, "\\b(trend|activity|last 30 days|over time|how active)\\b")) {
            return AssistantIntent.ACTIVITY_TREND;
        }
        if (matches(text, "\\b(alert|alerts)\\b")) {
            return AssistantIntent.ALERTS;
        }
        if (matches(text, "\\b(performance|how am i doing|portfolio|net worth|total value|all-time|all time)\\b")) {
            return AssistantIntent.PORTFOLIO_PERFORMANCE;
        }
        if (matches(text, "\\b(position|positions|holding|holdings|what do i (own|hold)|what am i holding)\\b")) {
            return AssistantIntent.POSITIONS;
        }
        return matchesAny(APP_WORDS, text) ? AssistantIntent.GENERAL : AssistantIntent.OFF_TOPIC;
    }

    /**
     * A reply for hello, thanks, goodbye and "what can you do", or null when the message is something else. Only a short
     * message that is nothing but small talk counts, so "hi, show my orders" goes on to be answered as a question.
     */
    public static String smallTalkReply(String message) {
        String text = normalise(message);
        if (text.isEmpty() || text.length() > 40) {
            return null;
        }
        if (GREETING.matcher(text).matches()) {
            return GREETING_REPLY;
        }
        if (THANKS.matcher(text).matches()) {
            return "You are welcome! Is there anything else you would like to know about your account?";
        }
        if (BYE.matcher(text).matches()) {
            return "Bye for now! I am here whenever you want to check on your account.";
        }
        if (HELP.matcher(text).matches()) {
            return HELP_REPLY;
        }
        return null;
    }

    /** A number the customer asked for ("last 10"), kept between 1 and the limit. */
    public static int requestedCount(String message, int fallback, int limit) {
        Matcher matcher = Pattern.compile("\\b(?:last|latest|recent|top|first)\\s+(\\d{1,3})\\b").matcher(normalise(message));
        if (matcher.find()) {
            return Math.max(1, Math.min(limit, Integer.parseInt(matcher.group(1))));
        }
        return fallback;
    }

    private static boolean matches(String text, String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(String... regexes) {
        return java.util.Arrays.stream(regexes).map(Pattern::compile).toList();
    }
}
