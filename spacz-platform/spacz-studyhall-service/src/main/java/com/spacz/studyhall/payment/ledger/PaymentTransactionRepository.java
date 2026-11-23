package com.spacz.studyhall.payment.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long>,
        JpaSpecificationExecutor<PaymentTransaction> {

    Optional<PaymentTransaction> findByPaymentIdAndType(Long paymentId, TransactionType type);

    List<PaymentTransaction> findByBookingIdOrderByOccurredAtAscIdAsc(Long bookingId);
}
