package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * SELECT ... FOR UPDATE on the seat row only: serialises concurrent bookings of the same seat
     * without blocking bookings of other seats.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    Optional<Seat> findByIdAndStudyHallId(Long id, Long studyHallId);

    Optional<Seat> findByLegacySeatId(Long legacySeatId);

    @EntityGraph(attributePaths = {"block", "studyHall", "studyHall.vendor"})
    @Query("select s from Seat s where s.id = :id and s.studyHall.vendor.vendorId = :vendorId")
    Optional<Seat> findOwned(@Param("id") Long id, @Param("vendorId") Long vendorId);

    @Query("select s from Seat s where s.studyHall.vendor.vendorId = :vendorId order by s.id")
    List<Seat> findAllOwned(@Param("vendorId") Long vendorId);

    List<Seat> findByStudyHallId(Long studyHallId);

    long countByStudyHallId(Long studyHallId);

    long countByStudyHallIdAndStatus(Long studyHallId, SeatStatus status);

    boolean existsByStudyHallIdAndSeatNumberIgnoreCase(Long studyHallId, String seatNumber);

    boolean existsByStudyHallIdAndSeatNumberIgnoreCaseAndIdNot(Long studyHallId, String seatNumber, Long id);

    @Query("select lower(s.seatNumber) from Seat s where s.studyHall.id = :hallId")
    Set<String> findSeatNumbersLowercase(@Param("hallId") Long studyHallId);

    @Query("select s.studyHall.id, count(s) from Seat s where s.studyHall.id in :hallIds and s.status = :status group by s.studyHall.id")
    List<Object[]> countByHallIdsAndStatus(@Param("hallIds") Collection<Long> hallIds, @Param("status") SeatStatus status);
}
