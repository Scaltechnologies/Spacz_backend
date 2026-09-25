package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Block;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByStudyHallIdOrderByDisplayOrderAscIdAsc(Long studyHallId);

    Optional<Block> findByIdAndStudyHallId(Long id, Long studyHallId);

    /** A block of any hall owned by the vendor (legacy API). */
    @EntityGraph(attributePaths = {"studyHall", "studyHall.vendor"})
    @Query("select b from Block b where b.id = :id and b.studyHall.vendor.vendorId = :vendorId")
    Optional<Block> findOwned(@Param("id") Long id, @Param("vendorId") Long vendorId);

    @Query("select b from Block b where b.studyHall.vendor.vendorId = :vendorId order by b.id")
    List<Block> findAllOwned(@Param("vendorId") Long vendorId);

    @Query("select coalesce(max(b.displayOrder), 0) from Block b where b.studyHall.id = :hallId")
    int maxDisplayOrder(@Param("hallId") Long studyHallId);
}
