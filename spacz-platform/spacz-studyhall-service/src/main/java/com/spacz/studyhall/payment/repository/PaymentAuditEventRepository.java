package com.spacz.studyhall.payment.repository;

import com.spacz.studyhall.payment.entity.PaymentAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentAuditEventRepository extends JpaRepository<PaymentAuditEvent, Long> {
    List<PaymentAuditEvent> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
}
