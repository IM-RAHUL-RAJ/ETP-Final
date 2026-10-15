package com.capstone.etp.automation;

/**
 * Where an automated order got to. A trigger is not a trade: STRATEGY_TRIGGERED means the rule fired and nothing
 * more; only ORDER_SUBMITTED means the Trade API accepted the order (and even then the executor decides if it fills).
 */
public enum OrderOutcome {
    STRATEGY_TRIGGERED,
    ORDER_SUBMITTED,
    /** The Trade API answered with a refusal (validation, authorisation, funds, ...). */
    ORDER_REJECTED,
    /** The Trade API could not be reached, timed out or failed. The outcome of the order is unknown. */
    ORDER_SUBMISSION_FAILED
}
