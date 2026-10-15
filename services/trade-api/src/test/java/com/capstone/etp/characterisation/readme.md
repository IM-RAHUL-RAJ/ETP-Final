# Order Placement Characterisation Tests

These tests **pin the current behavior** of the Order Placement Service before service changes are made.

The tests cover the following types of behavior:

| # | Test Type                     | What It Pins                                                                                     |
| - | ----------------------------- | ------------------------------------------------------------------------------------------------ |
| 1 | **Successful BUY Order**      | A valid BUY order is accepted and created with `NEW` status.                                     |
| 2 | **Successful SELL Order**     | A valid SELL order is accepted and created with `NEW` status.                                    |
| 3 | **Duplicate Idempotency Key** | A duplicate order is rejected with `DuplicateOrderException` and no new order is created.        |
| 4 | **Insufficient Funds**        | An unaffordable BUY order is rejected with `InsufficientFundsException` and no order is created. |
| 5 | **Unknown Instrument**        | An order for an unknown symbol is rejected with `InstrumentNotFoundException`.                   |
| 6 | **Inactive Account**          | An order from an inactive/suspended account is rejected with `AccountNotActiveException`.        |
| 7 | **Insufficient Holdings**     | A SELL order exceeding the available holdings is rejected with `InsufficientHoldingsException`.  |
| 8 | **Zero Quantity**             | An order with zero quantity is rejected as an invalid order value.                               |
| 9 | **Negative Price**            | An order with a negative price is rejected as an invalid order value.                            |

## Behavior Covered

The characterization tests capture:

* **Successful order creation**
* **BUY and SELL order paths**
* **Duplicate/idempotency handling**
* **Account validation**
* **Instrument validation**
* **Funds validation**
* **Holdings validation**
* **Order value validation**
* **Prevention of database writes when validation fails**