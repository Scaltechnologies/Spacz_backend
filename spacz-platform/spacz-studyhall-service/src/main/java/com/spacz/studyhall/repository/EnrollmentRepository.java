package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Enrollment;
import com.spacz.studyhall.entity.EnrollmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long>, JpaSpecificationExecutor<Enrollment> {

    Optional<Enrollment> findByStudyHallIdAndUserIdAndStatus(Long studyHallId, Long userId, EnrollmentStatus status);

    @EntityGraph(attributePaths = {"studyHall", "program", "seat"})
    List<Enrollment> findByUserIdOrderByStartDateDesc(Long userId);

    @EntityGraph(attributePaths = {"studyHall", "program", "seat"})
    Optional<Enrollment> findByIdAndStudyHallId(Long id, Long studyHallId);

    boolean existsBySeat_IdAndStatus(Long seatId, EnrollmentStatus status);

    boolean existsBySeat_Id(Long seatId);

    long countByStatus(EnrollmentStatus status);

    @Query("select e from Enrollment e where e.status = com.spacz.studyhall.entity.EnrollmentStatus.ACTIVE and e.endDate < :today")
    List<Enrollment> findEnded(@Param("today") LocalDate today, org.springframework.data.domain.Pageable pageable);
}
