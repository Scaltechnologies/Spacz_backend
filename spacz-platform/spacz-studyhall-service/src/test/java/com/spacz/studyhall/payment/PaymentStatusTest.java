package com.spacz.studyhall.payment;

import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static com.spacz.studyhall.payment.enums.PaymentStatus.AWAITING_VENDOR_CONFIRMATION;
import static com.spacz.studyhall.payment.enums.PaymentStatus.CANCELLED;
import static com.spacz.studyhall.payment.enums.PaymentStatus.CREATED;
import static com.spacz.studyhall.payment.enums.PaymentStatus.EXPIRED;
import static com.spacz.studyhall.payment.enums.PaymentStatus.FAILED;
import static com.spacz.studyhall.payment.enums.PaymentStatus.PENDING;
import static com.spacz.studyhall.payment.enums.PaymentStatus.PROCESSING;
import static com.spacz.studyhall.payment.enums.PaymentStatus.REFUNDED;
import static com.spacz.studyhall.payment.enums.PaymentStatus.REFUND_PENDING;
import static com.spacz.studyhall.payment.enums.PaymentStatus.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStatusTest {

    @Test
    void successAndRefundedAreImmutable() {
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(SUCCESS.canTransitionTo(next)).as("SUCCESS -> " + next).isFalse();
            assertThat(REFUNDED.canTransitionTo(next)).as("REFUNDED -> " + next).isFalse();
        }
    }

    @Test
    void transitionTable() {
        assertAllowed(CREATED, PENDING, CANCELLED, EXPIRED, FAILED);
        assertAllowed(PENDING, PROCESSING, SUCCESS, FAILED, CANCELLED, EXPIRED, AWAITING_VENDOR_CONFIRMATION, REFUND_PENDING);
        assertAllowed(PROCESSING, SUCCESS, FAILED, CANCELLED, EXPIRED, REFUND_PENDING);
        assertAllowed(AWAITING_VENDOR_CONFIRMATION, SUCCESS, CANCELLED, EXPIRED, PENDING);
        // A late gateway SUCCESS on a locally closed payment is reconciled forward, never lost.
        for (PaymentStatus closed : EnumSet.of(FAILED, CANCELLED, EXPIRED)) {
            assertAllowed(closed, PENDING, SUCCESS, REFUND_PENDING);
        }
        assertAllowed(REFUND_PENDING, REFUNDED);
    }

    @Test
    void classification() {
        assertThat(PaymentStatus.OPEN).containsExactly(CREATED, PENDING, PROCESSING, AWAITING_VENDOR_CONFIRMATION);
        assertThat(REFUND_PENDING.isTerminal()).isFalse();
        assertThat(REFUND_PENDING.isOpen()).isFalse();
        assertThat(REFUND_PENDING.isMoneySettled()).isTrue();
        assertThat(EnumSet.allOf(PaymentStatus.class)).filteredOn(PaymentStatus::isTerminal)
                .containsExactlyInAnyOrder(SUCCESS, FAILED, CANCELLED, EXPIRED, REFUNDED);
    }

    @Test
    void paymentRejectsIllegalMoves() {
        Payment payment = Payment.builder().status(SUCCESS).build();
        assertThatThrownBy(() -> payment.transitionTo(FAILED)).isInstanceOf(IllegalStateException.class);
        Payment open = Payment.builder().status(PENDING).build();
        open.transitionTo(AWAITING_VENDOR_CONFIRMATION);
        assertThat(open.getStatus()).isEqualTo(AWAITING_VENDOR_CONFIRMATION);
        assertThatThrownBy(() -> open.transitionTo(REFUND_PENDING)).isInstanceOf(IllegalStateException.class);
    }

    private static void assertAllowed(PaymentStatus from, PaymentStatus... allowed) {
        EnumSet<PaymentStatus> ok = EnumSet.of(from, allowed);
        for (PaymentStatus next : PaymentStatus.values()) {
            assertThat(from.canTransitionTo(next)).as(from + " -> " + next).isEqualTo(ok.contains(next));
        }
    }
}
