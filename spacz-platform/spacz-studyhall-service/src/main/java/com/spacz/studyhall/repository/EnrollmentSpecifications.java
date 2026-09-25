package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Enrollment;
import com.spacz.studyhall.entity.EnrollmentStatus;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Locale;

public final class EnrollmentSpecifications {

    private EnrollmentSpecifications() {
    }

    public static Specification<Enrollment> fetchDetails() {
        return (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                root.fetch("studyHall", JoinType.INNER);
                root.fetch("program", JoinType.LEFT);
                root.fetch("seat", JoinType.LEFT);
            }
            return null;
        };
    }

    public static Specification<Enrollment> forVendor(Long vendorId) {
        return (root, query, cb) -> cb.equal(root.get("studyHall").get("vendor").get("vendorId"), vendorId);
    }

    public static Specification<Enrollment> forHall(Long studyHallId) {
        return studyHallId == null ? null : (root, query, cb) -> cb.equal(root.get("studyHall").get("id"), studyHallId);
    }

    public static Specification<Enrollment> withStatus(EnrollmentStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Enrollment> forProgram(Long programId) {
        return programId == null ? null : (root, query, cb) -> cb.equal(root.get("program").get("id"), programId);
    }

    /** Matches walk-in guests by name / phone / email (account holders' names live in user-service). */
    public static Specification<Enrollment> guestMatches(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("guestName")), pattern),
                cb.like(cb.lower(root.get("guestPhone")), pattern),
                cb.like(cb.lower(root.get("guestEmail")), pattern));
    }
}
