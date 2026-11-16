package com.spacz.notification.repository;

import com.spacz.notification.entity.NotificationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationReceiptRepository extends JpaRepository<NotificationReceipt, Long> {

    Optional<NotificationReceipt> findByNotificationIdAndUserId(Long notificationId, Long userId);

    List<NotificationReceipt> findByUserIdAndNotificationIdIn(Long userId, Collection<Long> notificationIds);
}
