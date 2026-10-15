SELECT
    i.symbol,
    i.exchange,
    i.asset_class,
    h.quantity,
    h.avg_price
FROM portfolio_holdings h
JOIN portfolios p
    ON p.id = h.portfolio_id
JOIN instruments i
    ON i.id = h.instrument_id
WHERE p.account_id = 1
ORDER BY i.symbol;