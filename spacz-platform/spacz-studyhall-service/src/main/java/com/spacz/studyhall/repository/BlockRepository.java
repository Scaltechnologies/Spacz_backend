package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Block;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByStudyHallIdOrderByDisplayOrderAscIdAsc(Long studyHallId);

    Optional<Block> findByIdAndStudyHallId(Long id, Long studyHallId);

    boolean existsByFloorId(Long floorId);

    /** A block of any hall owned by the vendor (legacy API). */
    @EntityGraph(attributePaths = {"studyHall", "studyHall.vendor"})
    @Query("select b from Block b where b.id = :id and b.studyHall.vendor.vendorId = :vendorId")
    Optional<Block> findOwned(@Param("id") Long id, @Param("vendorId") Long vendorId);

    @Query("select b from Block b where b.studyHall.vendor.vendorId = :vendorId order by b.id")
    List<Block> findAllOwned(@Param("vendorId") Long vendorId);

    @Query("select coalesce(max(b.displayOrder), 0) from Block b where b.studyHall.id = :hallId")
    int maxDisplayOrder(@Param("hallId") Long studyHallId);

    // Bulk removal of one block and everything inside it: a fixed number of statements whatever the
    // block size, children first, so it is valid with or without the schema's ON DELETE CASCADE.
    // Callers must have checked that no booking or enrollment references the block's seats (those
    // foreign keys have no cascade, so PostgreSQL also rejects the seat delete if one does).

    @Modifying(flushAutomatically = true)
    @Query("delete from LayoutCell c where c.layout.id = :blockId")
    int deleteLayoutCellsOfBlock(@Param("blockId") Long blockId);

    @Modifying
    @Query("delete from LayoutFixture f where f.layout.id = :blockId")
    int deleteLayoutFixturesOfBlock(@Param("blockId") Long blockId);

    @Modifying
    @Query("delete from BlockLayout l where l.id = :blockId")
    int deleteLayoutOfBlock(@Param("blockId") Long blockId);

    @Modifying
    @Query("delete from Seat s where s.block.id = :blockId")
    int deleteSeatsOfBlock(@Param("blockId") Long blockId);

    /** Also removes the block's amenity links (Hibernate clears the block_amenities join table). */
    @Modifying(clearAutomatically = true)
    @Query("delete from Block b where b.id = :blockId")
    int deleteBlockRow(@Param("blockId") Long blockId);
}
