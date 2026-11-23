package com.spacz.studyhall.history;

import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.history.HistoryViews.BookingHistory;
import com.spacz.studyhall.history.HistoryViews.PaymentSummaryView;
import com.spacz.studyhall.history.HistoryViews.StatusChangeView;
import com.spacz.studyhall.history.HistoryViews.TransactionSummary;
import com.spacz.studyhall.history.HistoryViews.TransactionView;
import com.spacz.studyhall.payment.enums.PaymentMethod;
import com.spacz.studyhall.payment.ledger.PaymentTransaction;
import com.spacz.studyhall.payment.ledger.PaymentTransactionRepository;
import com.spacz.studyhall.payment.ledger.TransactionFilter;
import com.spacz.studyhall.payment.ledger.TransactionStatus;
import com.spacz.studyhall.payment.ledger.TransactionType;
import com.spacz.studyhall.payment.repository.PaymentRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.support.PageableSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read side of booking status history and the transaction ledger, for students, vendors and admins. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HistoryService {

    private static final Set<String> SORTABLE = Set.of("occurredAt", "amount", "completedAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "occurredAt")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final PaymentTransactionRepository transactionRepository;
    private final BookingStatusHistoryRepository historyRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingProperties bookingProperties;

    @PersistenceContext
    private EntityManager entityManager;

    /** Date-range and category filters shared by all ledger queries; dates are in the business time zone. */
    public record Query(Long studyHallId, Long bookingId, TransactionType type, TransactionStatus status,
                        PaymentMethod paymentMethod, LocalDate from, LocalDate to) {
    }

    // ── Students ─────────────────────────────────────────────────────────────

    public PageResponse<TransactionView> userTransactions(Long userId, Query q, Pageable pageable) {
        return page(filter(userId, null, q), pageable);
    }

    public TransactionView userTransaction(Long userId, Long transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(t -> t.getUserId().equals(userId))
                .map(TransactionView::of)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    // ── Vendors ──────────────────────────────────────────────────────────────

    public PageResponse<TransactionView> vendorTransactions(Long vendorId, Query q, Pageable pageable) {
        return page(filter(null, vendorId, q), pageable);
    }

    /** One transaction at the vendor's halls; 404 for any other vendor's. */
    public TransactionView vendorTransaction(Long vendorId, Long transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(t -> t.getVendorId().equals(vendorId))
                .map(TransactionView::of)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    public TransactionSummary vendorSummary(Long vendorId, Query q) {
        return summary(filter(null, vendorId, q), q);
    }

    // ── Admins (internal API) ────────────────────────────────────────────────

    public PageResponse<TransactionView> allTransactions(Long userId, Long vendorId, Query q, Pageable pageable) {
        return page(filter(userId, vendorId, q), pageable);
    }

    public TransactionSummary allSummary(Long userId, Long vendorId, Query q) {
        return summary(filter(userId, vendorId, q), q);
    }

    // ── Booking timeline ─────────────────────────────────────────────────────

    /** Visible to the booking's student, the hall's vendor and admins; 404 for everyone else. */
    public BookingHistory bookingHistory(AuthenticatedUser caller, Long bookingId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        boolean allowed = caller.isAdmin()
                || (caller.isUser() && booking.getUserId().equals(caller.userId()))
                || (caller.isVendor() && booking.getStudyHall().isOwnedBy(caller.userId()));
        if (!allowed) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return timeline(booking);
    }

    public BookingHistory bookingHistoryForAdmin(Long bookingId) {
        return timeline(bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId)));
    }

    private BookingHistory timeline(Booking booking) {
        List<PaymentSummaryView> payments = new ArrayList<>(paymentRepository
                .findByBookingIdOrderByCreatedAtDescIdDesc(booking.getId()).stream()
                .map(PaymentSummaryView::of).toList());
        Collections.reverse(payments);
        return new BookingHistory(booking.getId(), booking.getBookingReference(), booking.getStatus(),
                booking.getTotalPrice(),
                historyRepository.findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId()).stream()
                        .map(StatusChangeView::of).toList(),
                payments,
                transactionRepository.findByBookingIdOrderByOccurredAtAscIdAsc(booking.getId()).stream()
                        .map(TransactionView::of).toList());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TransactionFilter filter(Long userId, Long vendorId, Query q) {
        if (q.from() != null && q.to() != null && q.to().isBefore(q.from())) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "'to' must not be before 'from'");
        }
        Instant from = q.from() == null ? null : q.from().atStartOfDay(bookingProperties.zone()).toInstant();
        Instant toExclusive = q.to() == null ? null
                : q.to().plusDays(1).atStartOfDay(bookingProperties.zone()).toInstant();
        return new TransactionFilter(userId, vendorId, q.studyHallId(), q.bookingId(), q.type(), q.status(),
                q.paymentMethod(), from, toExclusive);
    }

    private PageResponse<TransactionView> page(TransactionFilter filter, Pageable pageable) {
        return PageResponse.from(transactionRepository.findAll(filter.toSpecification(),
                PageableSupport.restrictSort(pageable, SORTABLE, DEFAULT_SORT)), TransactionView::of);
    }

    /** One grouped aggregate query: (type, status, method) → count, sum. */
    private TransactionSummary summary(TransactionFilter filter, Query q) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<PaymentTransaction> root = query.from(PaymentTransaction.class);
        Expression<Long> count = cb.count(root);
        Expression<BigDecimal> sum = cb.sum(root.get("amount"));
        query.multiselect(root.get("type"), root.get("status"), root.get("paymentMethod"), count, sum)
                .where(filter.predicates(root, cb).toArray(Predicate[]::new))
                .groupBy(root.get("type"), root.get("status"), root.get("paymentMethod"));

        long paymentCount = 0;
        long refundCount = 0;
        BigDecimal collected = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal refundPending = BigDecimal.ZERO;
        Map<PaymentMethod, BigDecimal> byMethod = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : PaymentMethod.values()) {
            byMethod.put(method, BigDecimal.ZERO);
        }
        for (Tuple row : entityManager.createQuery(query).getResultList()) {
            TransactionType type = row.get(0, TransactionType.class);
            TransactionStatus status = row.get(1, TransactionStatus.class);
            PaymentMethod method = row.get(2, PaymentMethod.class);
            long n = row.get(3, Long.class);
            BigDecimal amount = row.get(4, BigDecimal.class) == null ? BigDecimal.ZERO : row.get(4, BigDecimal.class);
            if (type == TransactionType.PAYMENT) {
                paymentCount += n;
                collected = collected.add(amount);
                byMethod.merge(method, amount, BigDecimal::add);
            } else {
                refundCount += n;
                if (status == TransactionStatus.SUCCESS) {
                    refunded = refunded.add(amount);
                } else {
                    refundPending = refundPending.add(amount);
                }
            }
        }
        return new TransactionSummary(q.from(), q.to(), "INR", paymentCount, collected, refundCount, refunded,
                refundPending, collected.subtract(refunded).subtract(refundPending), byMethod);
    }
}
