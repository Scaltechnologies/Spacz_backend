package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class StudyHallSpecifications {

    private static final double KM_PER_DEGREE_LAT = 111.0;

    private StudyHallSpecifications() {
    }

    /** ACTIVE halls whose vendor is APPROVED or ACTIVE. */
    public static Specification<StudyHall> publiclyVisible() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), StudyHallStatus.ACTIVE),
                root.get("vendor").get("status").in(List.of(VendorStatus.APPROVED, VendorStatus.ACTIVE)));
    }

    public static Specification<StudyHall> textMatches(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern),
                cb.like(cb.lower(root.get("description")), pattern),
                cb.like(cb.lower(root.get("addressLine")), pattern),
                cb.like(cb.lower(root.get("city")), pattern));
    }

    public static Specification<StudyHall> inCity(String city) {
        if (!StringUtils.hasText(city)) {
            return null;
        }
        String normalized = city.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("city")), normalized);
    }

    public static Specification<StudyHall> hasStatus(StudyHallStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<StudyHall> ownedByVendor(Long vendorId) {
        return vendorId == null ? null : (root, query, cb) -> cb.equal(root.get("vendor").get("vendorId"), vendorId);
    }

    /** Filters on the hall's default daily price. */
    public static Specification<StudyHall> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min == null && max == null) {
                return null;
            }
            if (min != null && max != null) {
                return cb.between(root.get("pricePerDay"), min, max);
            }
            return min != null
                    ? cb.greaterThanOrEqualTo(root.get("pricePerDay"), min)
                    : cb.lessThanOrEqualTo(root.get("pricePerDay"), max);
        };
    }

    public static Specification<StudyHall> supportsProgram(Long programId) {
        if (programId == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<StudyHall> correlated = sub.correlate(root);
            Join<Object, Object> program = correlated.join("programs");
            sub.select(program.get("id")).where(cb.equal(program.get("id"), programId));
            return cb.exists(sub);
        };
    }

    /** The hall must offer ALL the given amenities (at hall level or in at least one block). */
    public static Specification<StudyHall> hasAllAmenities(Collection<Long> amenityIds) {
        if (amenityIds == null || amenityIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> cb.and(amenityIds.stream().map(amenityId -> {
            Subquery<Long> hallLevel = query.subquery(Long.class);
            Join<Object, Object> hallAmenity = hallLevel.correlate(root).join("amenities");
            hallLevel.select(hallAmenity.get("id")).where(cb.equal(hallAmenity.get("id"), amenityId));

            Subquery<Long> blockLevel = query.subquery(Long.class);
            Join<Object, Object> blockAmenity = blockLevel.correlate(root).join("blocks").join("amenities");
            blockLevel.select(blockAmenity.get("id")).where(cb.equal(blockAmenity.get("id"), amenityId));
            return cb.or(cb.exists(hallLevel), cb.exists(blockLevel));
        }).toArray(Predicate[]::new));
    }

    /** Bounding-box pre-filter; the exact distance is computed for the response. */
    public static Specification<StudyHall> withinRadius(Double latitude, Double longitude, Double radiusKm) {
        if (latitude == null || longitude == null || radiusKm == null) {
            return null;
        }
        double latDelta = radiusKm / KM_PER_DEGREE_LAT;
        double lngDelta = radiusKm / (KM_PER_DEGREE_LAT * Math.max(Math.cos(Math.toRadians(latitude)), 0.01));
        return (root, query, cb) -> cb.and(
                cb.between(root.get("latitude"), latitude - latDelta, latitude + latDelta),
                cb.between(root.get("longitude"), longitude - lngDelta, longitude + lngDelta));
    }

    /** At least one AVAILABLE seat with no occupying booking overlapping [from, to]. */
    public static Specification<StudyHall> hasFreeSeat(LocalDate from, LocalDate to, Instant now) {
        if (from == null || to == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> seatSub = query.subquery(Long.class);
            Root<Seat> seat = seatSub.from(Seat.class);

            Subquery<Long> bookingSub = seatSub.subquery(Long.class);
            Root<Booking> booking = bookingSub.from(Booking.class);
            bookingSub.select(booking.get("id")).where(
                    cb.equal(booking.get("seat"), seat),
                    cb.lessThanOrEqualTo(booking.get("startDate"), to),
                    cb.greaterThanOrEqualTo(booking.get("endDate"), from),
                    cb.or(
                            cb.equal(booking.get("status"), BookingStatus.CONFIRMED),
                            cb.and(cb.equal(booking.get("status"), BookingStatus.PENDING),
                                    cb.greaterThan(booking.get("holdExpiresAt"), now))));

            seatSub.select(seat.get("id")).where(
                    cb.equal(seat.get("studyHall"), root),
                    cb.equal(seat.get("status"), SeatStatus.AVAILABLE),
                    cb.not(cb.exists(bookingSub)));
            return cb.exists(seatSub);
        };
    }
}
