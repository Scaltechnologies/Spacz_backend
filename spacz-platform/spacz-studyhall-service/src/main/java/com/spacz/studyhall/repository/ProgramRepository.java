package com.spacz.studyhall.repository;

import com.spacz.studyhall.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface ProgramRepository extends JpaRepository<Program, Long>, JpaSpecificationExecutor<Program> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Program> findByIdIn(Collection<Long> ids);
}
