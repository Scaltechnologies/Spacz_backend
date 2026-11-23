package com.spacz.studyhall.wallet.entity;

import java.util.List;

/**
 * PENDING -> PROCESSING -> PAID, or PENDING / PROCESSING -> REJECTED (by an admin), or PENDING -> CANCELLED
 * (by the vendor). REJECTED and CANCELLED give the amount back to the wallet. PENDING may also go straight
 * to PAID. The status list of {@link #OPEN} must match uq_withdrawals_one_open_per_vendor (Flyway V14).
 */
public enum WithdrawalStatus {
    /** Requested by the vendor; the amount is reserved from the wallet. */
    PENDING,
    /** An admin accepted it and is sending the money. */
    PROCESSING,
    /** Money sent; payoutReference holds the UTR / transfer reference. */
    PAID,
    REJECTED,
    CANCELLED;

    public static final List<WithdrawalStatus> OPEN = List.of(PENDING, PROCESSING);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
