-- Probe:
-- This will fail with SQL STATE 23503 as:
-- The database must reject an order referencing an account that does not exist.

BEGIN;

INSERT INTO orders
(
    account_id,
    instrument_id,
    idempotency_key,
    order_type,
    quantity,
    limit_price,
    status
)
VALUES
(
    10,                     --> account does not exist
    1,
    'orphan-account-probe',
    'BUY',
    1,
    100.00000000,
    'NEW'
);

ROLLBACK;