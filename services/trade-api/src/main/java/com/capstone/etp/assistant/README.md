# In-app assistant (Gemini)

A companion in the app (**Ask** in the sidebar) that answers questions about the signed-in customer's **own** orders, positions, holdings, profit and loss, alerts and activity. It reads and answers. It cannot place, change or cancel anything, and it does not give investment advice.

Contract: `contracts/assistant-api.yaml`.

## How an answer is made

```
POST /api/v1/accounts/{accountId}/assistant/chat
  1. JWT must own {accountId} (403), and be within 20 questions a minute (429)
  2. deterministic screens, BEFORE any model
       prompt injection  -> refused (intent BLOCKED)
       advice question   -> refused, advisoryBlocked=true, redirectTo=/advice
  3. classify the question; not about the app -> declined (OFF_TOPIC)
  4. read the figures from the account's own data  -> dataCards + facts
  5. Gemini (server side) only PHRASES those facts
  6. output guard: advice, a leak, an essay, or a failed call -> the rules' own sentence is used instead
```

The **cards are always the data**. The model never supplies a number and never fetches anything: it is given the facts and the question, nothing else.

## Guardrails

| Risk | Control |
|---|---|
| Key exposure | The key is `GEMINI_API_KEY` in the server environment only; sent in a request header, never logged, returned or committed. The browser calls only the trade API |
| Other customers' data | `AccountAuthorizer.ensureOwnAccount` on every call; admin tokens (no accounts) are refused; the data services apply their own checks again |
| Investment advice | A screen for clear requests only ("what should I buy", "should I sell X", "stock tips", "recommend a stock", "buy or sell", "predict", "good time to buy"), then the system prompt, then an output guard that discards "you should buy", "I recommend", "price target". Everyday questions that merely contain words like *best*, *top*, *advice* or *buy* ("my best performing stock", "what did I buy last week") are answered. Answer: *"I cannot provide investment advice. Please use the Advice page for research signals."* with `redirectTo: /advice` |
| Prompt injection | A screen for "ignore previous instructions", "system prompt", "reveal your prompt", requests for *your* API key or token, "you are now", "pretend you are", "developer mode", "jailbreak" and fence tags, before the model. The question is fenced inside `<user_question>` and the prompt says it is untrusted; fence tags are stripped from it |
| Leaking the prompt or keys | The output guard rejects text mentioning the system prompt or the actual key |
| Invented figures | Figures come from the cards; the prompt forbids estimating; with no data the answer says so ("You have not placed any orders yet") |
| Cost abuse | 20 questions a minute per account, message length 500 |
| Model down or no key | The rules' sentence is used (`answeredBy: RULES`); the cards are unchanged |
| Actions | None. There are no tools or function calls |

## Questions it answers

Quick actions (also by plain words): portfolio performance (today and all-time), top gainers and losers, last N transactions, BUY vs SELL this week, working and recently rejected orders, stale or unpriced holdings, realised vs unrealised PnL, activity over 30 days. Also: positions, alerts, and a general account overview.

## Configuration

```
GEMINI_API_KEY=...           # required for model phrasing; leave empty to answer from the rules
GEMINI_MODEL=gemini-3.1-flash-lite
app.assistant.rate-per-minute=20
app.assistant.gemini.read-timeout-ms=8000
```

## Sample

```
POST /api/v1/accounts/1/assistant/chat
{ "message": "Show my last 2 transactions", "selectedQuickAction": "last_transactions" }

200
{ "answer": "Here are your 2 most recent orders. The latest is SELL 10 MSFT (NEW, 7 Oct, 11:55).",
  "intent": "RECENT_TRANSACTIONS", "advisoryBlocked": false, "redirectTo": null,
  "dataCards": [ { "kind": "TABLE", "title": "Last 2 transactions",
                   "columns": ["When","Symbol","Side","Qty","Price","Status"],
                   "rows": [["7 Oct, 11:55","MSFT","SELL","10","100.00","NEW"],
                            ["7 Oct, 10:00","AAPL","BUY","10","232.71","FILLED"]] } ],
  "citations": ["orders"], "answeredBy": "GEMINI" }

POST ... { "message": "What should I buy now?" }
200 { "answer": "I cannot provide investment advice. Please use the Advice page for research signals.",
      "intent": "ADVISORY", "advisoryBlocked": true, "redirectTo": "/advice", "dataCards": [] }
```

## Limits and next steps

- The screens are keyword and pattern based: a determined paraphrase of an advice question may get through the screen, and is then caught by the system prompt and the output guard. Add a classifier call or an allow-list of intents to tighten it.
- No memory between questions beyond a conversation id; follow-ups like "and last month?" are not understood.
- The rate limit is in memory per instance.
- Questions about a specific symbol ("how is AAPL doing?") get the overview, not a per-symbol answer.
- Not covered: funds movements history, notifications, watchlist content.

## Test

```
mvn test -Dtest='Assistant*Test,RestGeminiClientTest'
```
