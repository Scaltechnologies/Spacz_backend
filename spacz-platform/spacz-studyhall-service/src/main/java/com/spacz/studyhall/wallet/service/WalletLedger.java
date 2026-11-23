package com.spacz.studyhall.wallet.service;

import com.spacz.studyhall.config.WalletProperties;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.payment.entity.Payment;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.wallet.entity.WalletEntry;
import com.spacz.studyhall.wallet.entity.WalletEntryType;
import com.spacz.studyhall.wallet.repository.WalletEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;

/**
 * Turns money movements of the payment ledger into vendor wallet entries. Called by TransactionLedger inside
 * the payment transaction (under the payment row lock), so a wallet entry commits together with the payment
 * change it reflects. Idempotent: (payment_id, type) is unique and an existing entry is kept.
 *
 * <ul>
 *   <li>ONLINE payment that confirmed a booking: credit the vendor's share (seat price - discount - commission),
 *       withdrawable after the settlement hold.</li>
 *   <li>PAY_AT_HALL payment: the vendor kept the whole amount, so debit what belongs to SPACZ (platform charges +
 *       tax + commission), if anything.</li>
 *   <li>Refund of a payment that had been credited: reverse the credit.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLedger {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WalletEntryRepository entries;
    private final BookingRepository bookings;
    private final WalletProperties properties;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void onPaymentReceived(Payment payment) {
        // A capture that could not confirm its booking is refunded straight away: nothing for the vendor.
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            return;
        }
        Booking booking = bookings.findById(payment.getBookingId()).orElse(null);
        if (booking == null) {
            log.error("Wallet: booking {} of payment {} not found; no wallet entry", payment.getBookingId(), payment.getId());
            return;
        }
        BigDecimal seatRevenue = nz(booking.getBaseAmount()).subtract(nz(booking.getDiscountAmount()));
        BigDecimal commission = seatRevenue.multiply(properties.commissionPercent())
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        Instant now = clock.instant();

        if (payment.getPaymentMethod() == PaymentMethod.ONLINE) {
            BigDecimal earning = seatRevenue.subtract(commission);
            if (earning.signum() > 0) {
                add(payment, WalletEntryType.BOOKING_EARNING, earning, now.plus(properties.settlementHold()),
                        "Earning for booking " + payment.getBookingReference()
                                + (commission.signum() > 0 ? " (after Rs." + commission + " commission)" : ""));
            }
        } else {
            BigDecimal due = nz(booking.getPlatformCharges()).add(nz(booking.getTaxAmount())).add(commission);
            if (due.signum() > 0) {
                add(payment, WalletEntryType.PAY_AT_HALL_FEE, due.negate(), now,
                        "Platform charges, tax and commission collected at the hall for booking "
                                + payment.getBookingReference());
            }
        }
    }

    /** A refund of this payment was initiated: take back what the vendor was credited for it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void onRefundInitiated(Payment payment) {
        entries.findByPaymentIdAndType(payment.getId(), WalletEntryType.BOOKING_EARNING).ifPresent(earning ->
                // Same available_at as the credit: a refund inside the hold never touches the available balance.
                add(payment, WalletEntryType.BOOKING_REFUND, earning.getAmount().negate(), earning.getAvailableAt(),
                        "Refund of booking " + payment.getBookingReference()));
    }

    private void add(Payment payment, WalletEntryType type, BigDecimal amount, Instant availableAt, String description) {
        if (entries.findByPaymentIdAndType(payment.getId(), type).isPresent()) {
            return;
        }
        entries.save(WalletEntry.builder()
                .vendorId(payment.getVendorId())
                .type(type)
                .amount(amount)
                .currency(payment.getCurrency())
                .availableAt(availableAt)
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .bookingReference(payment.getBookingReference())
                .description(description)
                .build());
        log.info("Wallet: {} {} for vendor {} (payment {})", type, amount, payment.getVendorId(), payment.getId());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
