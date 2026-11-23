package com.spacz.studyhall.payment.ledger;

public enum TransactionType {
    /** Money received from the student: a Razorpay capture or a vendor-confirmed Pay-at-Hall collection. */
    PAYMENT,
    /** Money returned to the student (always the full amount of the payment). */
    REFUND
}
