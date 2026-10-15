package com.capstone.etp.advice;

/** What the rules concluded. INSUFFICIENT_DATA is an explicit "no signal": it is never a BUY, SELL or HOLD. */
public enum AdviceSignal {
    BUY,
    SELL,
    HOLD,
    INSUFFICIENT_DATA
}
