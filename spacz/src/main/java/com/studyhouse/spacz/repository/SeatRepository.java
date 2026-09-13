package com.studyhouse.spacz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.entity.Seat;

@Repository
public interface SeatRepository extends JpaRepository<Seat, Long> {
}
