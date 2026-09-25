package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class BookingSpecifications {

    private BookingSpecifications() {
    }

    /** Fetches hall and seat for list responses (skipped for the count query). */
    public static Specification<Booking> fetchHallAndSeat() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("studyHall", JoinType.INNER);
                root.fetch("seat", JoinType.INNER);
                root.fetch("program", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Booking> forHall(Long studyHallId) {
        return studyHallId == null ? null : (root, query, cb) -> cb.equal(root.get("studyHall").get("id"), studyHallId);
    }

    public static Specification<Booking> forVendor(Long vendorId) {
        return vendorId == null ? null
                : (root, query, cb) -> cb.equal(root.get("studyHall").get("vendor").get("vendorId"), vendorId);
    }

    public static Specification<Booking> forUser(Long userId) {
        return userId == null ? null : (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Booking> withStatus(BookingStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Bookings whose date range overlaps [from, to]; either bound may be open. */
    public static Specification<Booking> overlapping(LocalDate from, LocalDate to) {
        return (root, query, cb) -> {
            if (from == null && to == null) {
                return null;
            }
            if (from != null && to != null) {
                return cb.and(cb.lessThanOrEqualTo(root.get("startDate"), to),
                        cb.greaterThanOrEqualTo(root.get("endDate"), from));
            }
            return from != null
                    ? cb.greaterThanOrEqualTo(root.get("endDate"), from)
                    : cb.lessThanOrEqualTo(root.get("startDate"), to);
        };
    }
}
