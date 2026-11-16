package com.spacz.notification.repository;

import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserId(Long userId);

    boolean existsByUserIdAndCategoryAndPushEnabledFalse(Long userId, NotificationCategory category);
}
