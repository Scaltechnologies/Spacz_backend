package com.studyhouse.spacz.partner.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.partner.entity.Amenity;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    Optional<Amenity> findByBlockBlockId(Long blockId);

    // Block has no mapped reference to its Amenity (the FK lives on amenity.block_id),
    // so JPA cascades from Owner/Property/Block cannot reach it. These remove the
    // amenity rows first so deleting the parent does not violate that FK.

    @Modifying(flushAutomatically = true)
    @Query("delete from Amenity a where a.block.blockId = :blockId")
    int deleteByBlockId(@Param("blockId") Long blockId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Amenity a where a.block.blockId in "
            + "(select b.blockId from Block b where b.property.propertyId = :propertyId)")
    int deleteByPropertyId(@Param("propertyId") Long propertyId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Amenity a where a.block.blockId in "
            + "(select b.blockId from Block b where b.property.owner.ownerId = :ownerId)")
    int deleteByOwnerId(@Param("ownerId") Long ownerId);
}
