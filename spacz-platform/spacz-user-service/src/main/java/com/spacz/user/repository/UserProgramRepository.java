package com.spacz.user.repository;

import com.spacz.user.entity.UserProgram;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProgramRepository extends JpaRepository<UserProgram, Long> {

    List<UserProgram> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<UserProgram> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndProgramId(Long userId, Long programId);
}
