SELECT
    o.id,
    i.symbol,
    o.order_type,
    o.quantity,
    o.limit_price,
    o.status,
    o.created_at,
    o.terminal_at
FROM orders o
JOIN instruments i
    ON i.id = o.instrument_id
WHERE o.account_id = 1
ORDER BY o.created_at DESC
LIMIT 50;