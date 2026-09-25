package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    /** Does any seat-occupying booking overlap [start, end] (inclusive)? */
    @Query("""
            select count(b) > 0 from Booking b
            where b.seat.id = :seatId
              and b.status in :statuses
              and b.startDate <= :endDate and b.endDate >= :startDate""")
    boolean existsOverlapping(@Param("seatId") Long seatId, @Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate, @Param("statuses") List<BookingStatus> statuses);

    /** Any confirmed booking or live hold on the seat that ends on or after the given day. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.seat.id = :seatId and b.endDate >= :from
              and (b.status = com.spacz.studyhall.entity.BookingStatus.CONFIRMED
                   or (b.status = com.spacz.studyhall.entity.BookingStatus.PENDING and b.holdExpiresAt > :now))""")
    boolean existsUpcomingOnSeat(@Param("seatId") Long seatId, @Param("from") LocalDate from, @Param("now") Instant now);

    /** Does the user already have a confirmed booking or live hold in the same hall on any of these dates? */
    @Query("""
            select count(b) > 0 from Booking b
            where b.userId = :userId and b.studyHall.id = :hallId
              and b.startDate <= :endDate and b.endDate >= :startDate
              and (b.status = com.spacz.studyhall.entity.BookingStatus.CONFIRMED
                   or (b.status = com.spacz.studyhall.entity.BookingStatus.PENDING and b.holdExpiresAt > :now))""")
    boolean existsUserOverlap(@Param("userId") Long userId, @Param("hallId") Long studyHallId,
                              @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
                              @Param("now") Instant now);

    /** Releases PENDING holds on this seat whose hold time has passed. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Booking b set b.status = :expired, b.updatedAt = :now, b.version = b.version + 1
            where b.seat.id = :seatId and b.status = :pending and b.holdExpiresAt <= :now""")
    int expireStaleHolds(@Param("seatId") Long seatId, @Param("now") Instant now,
                         @Param("pending") BookingStatus pending, @Param("expired") BookingStatus expired);

    /** Seats in the hall occupied by a CONFIRMED booking or a live PENDING hold overlapping [start, end]. */
    @Query("""
            select distinct b.seat.id from Booking b
            where b.studyHall.id = :hallId
              and b.startDate <= :endDate and b.endDate >= :startDate
              and (b.status = com.spacz.studyhall.entity.BookingStatus.CONFIRMED
                   or (b.status = com.spacz.studyhall.entity.BookingStatus.PENDING and b.holdExpiresAt > :now))""")
    Set<Long> findOccupiedSeatIds(@Param("hallId") Long studyHallId, @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate, @Param("now") Instant now);

    @EntityGraph(attributePaths = {"studyHall", "seat", "program"})
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findDetailed(@Param("id") Long id);

    @EntityGraph(attributePaths = {"studyHall", "seat", "program"})
    Page<Booking> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"studyHall", "seat", "program"})
    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);

    @Query("select b from Booking b where b.status = :status and b.holdExpiresAt <= :now")
    List<Booking> findExpiredHolds(@Param("status") BookingStatus pending, @Param("now") Instant now, Pageable pageable);

    @Query("select b from Booking b where b.status = :status and b.endDate < :today")
    List<Booking> findFinished(@Param("status") BookingStatus confirmed, @Param("today") LocalDate today, Pageable pageable);

    Optional<Booking> findByLegacyBookingId(Long legacyBookingId);

    boolean existsBySeat_Block_Id(Long blockId);

    boolean existsBySeat_Id(Long seatId);

    boolean existsByStudyHallId(Long studyHallId);

    @Query("select count(b) > 0 from Booking b where b.studyHall.vendor.id = :vendorProfileId")
    boolean existsForVendor(@Param("vendorProfileId") Long vendorProfileId);

    long countByStatus(BookingStatus status);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    @Query("""
            select count(b) from Booking b
            where b.status = com.spacz.studyhall.entity.BookingStatus.CONFIRMED
              and b.startDate <= :day and b.endDate >= :day""")
    long countConfirmedOn(@Param("day") LocalDate day);
}
