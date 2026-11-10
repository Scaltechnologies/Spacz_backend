package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Floor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, Long> {

    List<Floor> findByStudyHallIdOrderByDisplayOrderAscFloorNumberAsc(Long studyHallId);

    Optional<Floor> findByIdAndStudyHallId(Long id, Long studyHallId);

    boolean existsByStudyHallIdAndFloorNumber(Long studyHallId, int floorNumber);

    boolean existsByStudyHallIdAndFloorNumberAndIdNot(Long studyHallId, int floorNumber, Long id);

    @Query("select coalesce(max(f.displayOrder), -1) from Floor f where f.studyHall.id = :hallId")
    int maxDisplayOrder(@Param("hallId") Long studyHallId);
}
