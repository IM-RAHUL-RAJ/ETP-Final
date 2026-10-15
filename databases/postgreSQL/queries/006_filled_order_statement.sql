WITH filled_orders AS (
    SELECT
        o.id AS order_id,
        o.account_id,
        o.instrument_id,
        o.created_at,
        t.quantity,
        t.executed_price,
        (t.quantity * t.executed_price) AS order_value
    FROM orders o
    JOIN transactions t
        ON t.order_id = o.id
    WHERE o.account_id = 1
      AND o.status = 'FILLED'
)

SELECT
    order_id,
    account_id,
    instrument_id,
    created_at,
    quantity,
    executed_price,
    order_value,

    -- Running total of cash committed over time
    SUM(order_value) OVER (
        ORDER BY created_at ASC
    ) AS running_cash_committed,

    -- Rank orders by value within each instrument
    RANK() OVER (
        PARTITION BY instrument_id
        ORDER BY order_value DESC
    ) AS instrument_value_rank

FROM filled_orders
ORDER BY created_at ASC;