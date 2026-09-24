package com.spacz.user.repository;

import com.spacz.user.entity.ActivityType;
import com.spacz.user.entity.UserActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {

    Page<UserActivity> findByUserId(Long userId, Pageable pageable);

    Page<UserActivity> findByUserIdAndType(Long userId, ActivityType type, Pageable pageable);
}
