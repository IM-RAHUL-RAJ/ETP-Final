# Seeing automated trading work: a walkthrough

Two ways to watch a strategy trade. The **demo** takes about a minute. The **live** way needs real price movement and can take a long time.

> Automated trading places **real orders** through the Trade REST API, on the account you choose. Use a test account and a small quantity.

## Before you start

1. Run the migration once: `databases/postgreSQL/migrations/005_automation.sql` (a fresh `docker-compose up` does it for you).
2. In `.env`, for the demo button, set `AUTOMATION_SIMULATION=true`. Leave `AUTOMATION_ENABLED` at its default (true). Rebuild: `docker-compose up -d --build backend`.
3. Check the executor is running and publishing quotes to `market-data` (the stream the strategies read). The Watchlist page showing live prices is a good sign.

## A. The demo (about a minute)

1. Sign in as a customer. Open **Automation** in the sidebar.
2. Press **New strategy**.
   - Rule: *Moving-average crossover*.
   - Instrument: one that has a live price (one on your Watchlist).
   - Quantity: `1`.
   - Short `2`, long `4` (small numbers so it needs few prices).
   - Press **Create**. It appears as **Off**.
3. Press its switch. Read the confirmation (it names your account reference), tick **I understand**, press **Switch on**. The state becomes **On**.
4. The strategy **starts on its own**: it loads recent prices (15-minute candles) for the instrument, so it is usually **ready to act at once**; the row says *Watching the price: ready to act* and the chart draws. If recent prices cannot be loaded it says *Collecting prices: n of 5* and warms up from live quotes instead. Creating the strategy also puts the instrument on your Watchlist, because live prices only flow for instruments you hold, watch or have an order on. (For the demo you can skip waiting.)
5. In the strategy's panel under the row, press **Trigger BUY** (it is there because `AUTOMATION_SIMULATION=true`). Confirm the dialog: it states the real order it will place.
6. Within a few seconds **Recent activity** shows, newest first:
   - `Triggered`: the rule fired (detail says *demo price path*, with the short and long averages crossing).
   - `Order sent`: the Trade API accepted the order. If it says `Rejected`, the detail has the reason (for example not enough cash).
7. Open **Orders** (the blotter): the new BUY is there, `NEW` and then `FILLED` once the executor fills it. Your **Portfolio** and **Inbox** (order filled message) update. The mascot cheers when the fill notification arrives.
8. Press **Trigger SELL**: it crosses the other way and places a SELL.
9. Switch the strategy **Off** (or remove it) when you are done.

For a **Bollinger** strategy: Rule *Bollinger bands*, period `10` or more, width `2`. *Trigger BUY* walks the price below the lower band, *Trigger SELL* above the upper band. (A window under about 10 prices cannot be crossed by a single move, so the demo says so.)

What the demo does and does not do: it feeds a scripted price path **only to your one strategy**, never into the shared price history, so it cannot trigger anyone else's strategy. The order itself is real.

## B. Live (the real thing)

1. Create and switch on a strategy as above, on an instrument whose price moves (the executor polls the price provider).
2. It warms up from recent prices by itself (and live quotes then keep it current). The executor publishes a quote about once a minute, so the line moves slowly; a crossing needs the price to actually move. When the market is closed or the price is static, nothing crosses: use the demo.
3. The chart shows the price and the two averages (or the three Bollinger bands). When the short line crosses the long line (or the price pierces a band), the rule fires once, and the page shows `Triggered` then `Order sent`.
4. A crossover that is already in place does not fire again; the next trigger is the next crossing.

## What to look at when something does not happen

| You see | Why |
|---|---|
| Switch is disabled | The strategy is `Invalid`; the reasons are listed under it |
| "Collecting prices" never moves | No quotes are arriving on `market-data` for that symbol: check the executor |
| No demo buttons | `AUTOMATION_SIMULATION` is not `true`, or the strategy is Off |
| Demo says there is no live price | The platform has not yet received a quote for that symbol |
| `Rejected` in Recent activity | The Trade API refused the order; the detail says why (cash, holdings, limits) |
| `Failed` | The Trade API could not be reached or timed out; the outcome is unknown and it is **not** retried |
| Nothing at all, "switched off on this platform" | `AUTOMATION_ENABLED=false` (the kill switch) |

Logs: `docker logs <backend> | grep AUTOMATION` shows `strategy active`, `event=STRATEGY_TRIGGERED`, `order submission attempt`, `event=ORDER_SUBMITTED` / `ORDER_REJECTED` / `ORDER_SUBMISSION_FAILED`.
