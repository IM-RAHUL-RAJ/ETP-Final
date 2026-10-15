# Trade Data Retention Design

## 1. Overview

This document describes the design for retaining executed trade data, how the data is populated, how downstream systems extract it, and how the design behaves as trade volume increases.

The database separates two different concepts:

- **Orders** represent customer trading intent.
- **Transactions** represent actual executed trades.

This separation ensures that historical reporting reflects the real execution rather than only the original request.

---

# 2. Retention Grain

The retained historical trade grain is: **One row per executed trade transaction.**

The `transactions` table is the historical record of completed executions.

Each transaction stores:

- transaction id;
- related order id;
- executed quantity;
- executed price;
- execution timestamp;
- execution status.

> The `orders` table does not represent the final trade history because an order contains the requested information, which **may differ** from the final execution details.

Executed transactions are treated as immutable historical records because changing completed trades would affect reporting.

---

# 3. Data Population

Executed trade history is populated by the execution workflow as follows:

```
      Client
        |
        v
  Trade REST API
        |
        v
    orders table
  (customer request)
        |
        v
Trade execution component
        |
        v
  transactions table
  (executed trade history)
```

### Order Creation

When a customer submits an order:

- the request is validated and order is stored;
- the order receives an initial lifecycle state - **NEW**.

The order record contains customer intent:

- account;
- instrument;
- order type;
- requested quantity;
- requested price;
- idempotency key.

#### Trade Execution

After execution completes:

- the execution component creates a transaction record;
- the transaction stores the actual executed quantity and price;
- the execution timestamp is recorded.

The transaction is created by the execution process, not directly by the customer request.

---

# 5. Behaviour at 100x Volume

The current design supports growth because executed transactions are stored as append-oriented historical records.

The main scaling considerations are:

### Query Performance

Performance is maintained through:

> - primary key lookups;
> - execution timestamp indexes;
> - incremental extraction instead of full table scans.

### Write Behaviour - **Append only**

The transaction table receives new rows when trades execute.

The design avoids unnecessary updates to historical rows because executed transactions should remain stable.

---

# 7. Archival and Retention

Executed transactions should be retained because they represent the official historical trade record.

They support:

- reporting;
- reconciliation;
- customer statements;
- audit requirements.

> Archived data should remain immutable.

---

# 8. Index and Write Cost Considerations

Indexes improve read performance but increase write cost. Every transaction insert must maintain the primary key index.

Additional indexes increase:

- insert processing time;
- storage requirements;
- maintenance overhead.

The design therefore avoids indexing every column and only adds indexes that support known access patterns.

---

# 9. Summary

- The design retains executed trades at transaction level.
- Orders represent customer intent.
- Transactions represent actual execution history.
- Historical trade records are populated after successful execution by the execution workflow.
- Incremental extraction uses transaction identifiers as a watermark instead of scanning the entire history.
- The current design avoids premature partitioning while leaving a clear path for future scaling through partitioning and archival strategies.