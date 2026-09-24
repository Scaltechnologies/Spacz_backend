package com.studyhouse.spacz.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.partner.entity.Block;

@Repository
public interface BlockRepository extends JpaRepository<Block, Long> {

    List<Block> findByPropertyPropertyIdOrderByBlockIdAsc(Long propertyId);
}
