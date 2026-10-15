SELECT
    o.id,
    o.order_type,
    i.symbol,
    o.quantity,
    o.limit_price,
    o.status,
    o.created_at
FROM orders o
JOIN instruments i
    ON i.id = o.instrument_id
WHERE o.account_id = 1
AND o.status = 'NEW'
ORDER BY o.created_at DESC;