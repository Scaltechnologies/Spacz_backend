package com.spacz.studyhall.payment.repository;

import com.spacz.studyhall.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    Optional<PaymentAttempt> findByGatewayOrderId(String gatewayOrderId);

    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberDesc(Long paymentId);

    int countByPaymentId(Long paymentId);
}
