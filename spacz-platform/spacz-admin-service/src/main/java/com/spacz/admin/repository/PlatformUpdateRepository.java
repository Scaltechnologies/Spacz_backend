package com.spacz.admin.repository;

import com.spacz.admin.entity.PlatformUpdate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformUpdateRepository extends JpaRepository<PlatformUpdate, Long> {

    Page<PlatformUpdate> findByArchivedFalse(Pageable pageable);

    List<PlatformUpdate> findByPublishedTrueAndArchivedFalseOrderByPublishedAtDesc();
}
