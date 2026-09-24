package com.studyhouse.spacz.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.partner.entity.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByBlockBlockIdOrderBySeatIdAsc(Long blockId);
}
