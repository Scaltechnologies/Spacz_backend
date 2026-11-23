package com.spacz.studyhall.wallet.entity;

/** Credits are positive amounts, debits negative. */
public enum WalletEntryType {
    /** + vendor's share of an ONLINE payment received by SPACZ. */
    BOOKING_EARNING,
    /** - that share reversed because the payment was refunded. */
    BOOKING_REFUND,
    /** - platform charges, tax and commission of a Pay at Hall booking (the vendor kept all the cash). */
    PAY_AT_HALL_FEE,
    /** - amount reserved by a withdrawal request. */
    WITHDRAWAL,
    /** + amount released again: the withdrawal was rejected or cancelled. */
    WITHDRAWAL_REVERSAL
}
