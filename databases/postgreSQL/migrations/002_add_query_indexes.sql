BEGIN;

SET LOCAL search_path TO trade, public;

-- Index for QUERY 1
-- (All open orders for one account, newest first)

CREATE INDEX idx_orders_account_status_created
ON orders (
    account_id,
    status,
    created_at DESC
);

-- Index for QUERY 2
-- (Last 50 orders for one account in any state, newest first)

CREATE INDEX idx_orders_account_created
ON orders (
    account_id,
    created_at DESC
);

-- Index for QUERY 4
-- (Every order created since a timestamp)

CREATE INDEX idx_orders_created_at
ON orders (
    created_at
);


COMMIT;