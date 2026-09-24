package com.studyhouse.spacz.partner.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.partner.entity.Property;

@Repository
public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByOwnerOwnerIdOrderByPropertyIdAsc(Long ownerId);
}
