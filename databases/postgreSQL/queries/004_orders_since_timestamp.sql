SELECT
    o.id,
    a.account_ref,
    i.symbol,
    o.order_type,
    o.quantity,
    o.limit_price,
    o.status,
    o.created_at
FROM orders o
JOIN accounts a
    ON a.id = o.account_id
JOIN instruments i
    ON i.id = o.instrument_id
WHERE o.created_at >= '2026-01-01 00:00:00+00'
ORDER BY o.created_at;