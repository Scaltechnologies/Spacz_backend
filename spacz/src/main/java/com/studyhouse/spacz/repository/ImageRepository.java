package com.studyhouse.spacz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.studyhouse.spacz.entity.Image;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
}