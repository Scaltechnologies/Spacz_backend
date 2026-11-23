package com.spacz.studyhall.payment.ledger;

public enum TransactionStatus {
    /** Refund initiated; the refund job has not completed it at the gateway yet. */
    PENDING,
    SUCCESS
}
