package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.BlockLayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Layouts share their block's id: {@code findById(blockId)}. */
public interface BlockLayoutRepository extends JpaRepository<BlockLayout, Long> {

    /** Every layout of the hall with its cells and their seats, in one query (layout endpoint). */
    @Query("""
            select distinct l from BlockLayout l
              left join fetch l.cells c
              left join fetch c.seat
            where l.block.studyHall.id = :hallId""")
    List<BlockLayout> findByStudyHallIdWithCells(@Param("hallId") Long studyHallId);
}
