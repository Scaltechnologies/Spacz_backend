package com.spacz.notification.repository;

import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByDedupKey(String dedupKey);

    /** Notifications an administrator sent by hand (admin panel history). */
    Page<Notification> findByCreatedByIsNotNull(Pageable pageable);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientUserId = :userId and n.readAt is null")
    int markAllPersonalRead(@Param("userId") Long userId, @Param("now") Instant now);

    /** Broadcasts visible to the account that it has not read (no receipt yet). */
    @Query("""
            select n.id from Notification n
            where n.recipientUserId is null and n.audience in :audiences
              and not exists (select r.id from NotificationReceipt r where r.notificationId = n.id and r.userId = :userId)
            """)
    List<Long> unreadBroadcastIds(@Param("userId") Long userId, @Param("audiences") Collection<Audience> audiences);

    @Modifying
    @Query("delete from Notification n where n.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
