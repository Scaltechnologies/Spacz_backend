package com.spacz.notification.job;

import com.spacz.notification.config.NotificationProperties;
import com.spacz.notification.repository.DeviceTokenRepository;
import com.spacz.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Nightly: deletes old notifications (their receipts go with them, ON DELETE CASCADE) and device
 * tokens the apps stopped refreshing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCleanupJob {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${spacz.notifications.cleanup-cron}")
    @Transactional
    public void cleanUp() {
        Instant now = clock.instant();
        int notifications = notificationRepository.deleteCreatedBefore(now.minus(properties.retention()));
        int devices = deviceTokenRepository.deleteNotSeenSince(now.minus(properties.deviceTokenTtl()));
        if (notifications > 0 || devices > 0) {
            log.info("Cleanup: deleted {} old notification(s) and {} stale device token(s)", notifications, devices);
        }
    }
}
