SELECT
    a.id,
    a.account_ref,
    c.client_ref,
    c.name,
    a.status,
    a.currency,
    a.cash_balance
FROM accounts a
JOIN clients c
    ON c.id = a.client_id
WHERE a.account_ref = 'ACC0000001';