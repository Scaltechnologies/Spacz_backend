package com.spacz.admin.repository;

import com.spacz.admin.entity.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Page<Offer> findByArchivedFalse(Pageable pageable);

    /** Live offers: active, not archived, now within [startAt, endAt). Highest priority first. */
    @Query("""
            select o from Offer o
            where o.active = true and o.archived = false and o.startAt <= :now and o.endAt > :now
            order by o.priority desc, o.createdAt desc""")
    List<Offer> findLive(@Param("now") Instant now);
}
