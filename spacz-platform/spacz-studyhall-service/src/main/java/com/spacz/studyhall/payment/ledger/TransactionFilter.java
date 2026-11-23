package com.spacz.studyhall.payment.ledger;

import com.spacz.studyhall.payment.enums.PaymentMethod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional ledger filters; a null field means "no filter".
 *
 * @param from        inclusive, on occurredAt
 * @param toExclusive exclusive, on occurredAt
 */
public record TransactionFilter(Long userId, Long vendorId, Long studyHallId, Long bookingId,
                                TransactionType type, TransactionStatus status, PaymentMethod paymentMethod,
                                Instant from, Instant toExclusive) {

    public Specification<PaymentTransaction> toSpecification() {
        return (root, query, cb) -> cb.and(predicates(root, cb).toArray(Predicate[]::new));
    }

    /** Shared by the paged list (Specification) and the summary (Criteria aggregate). */
    public List<Predicate> predicates(Root<PaymentTransaction> root, CriteriaBuilder cb) {
        List<Predicate> p = new ArrayList<>();
        eq(p, root, cb, "userId", userId);
        eq(p, root, cb, "vendorId", vendorId);
        eq(p, root, cb, "studyHallId", studyHallId);
        eq(p, root, cb, "bookingId", bookingId);
        eq(p, root, cb, "type", type);
        eq(p, root, cb, "status", status);
        eq(p, root, cb, "paymentMethod", paymentMethod);
        if (from != null) {
            p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (toExclusive != null) {
            p.add(cb.lessThan(root.get("occurredAt"), toExclusive));
        }
        return p;
    }

    private static void eq(List<Predicate> p, Root<PaymentTransaction> root, CriteriaBuilder cb, String attribute,
                           Object value) {
        if (value != null) {
            p.add(cb.equal(root.get(attribute), value));
        }
    }
}
