package com.spacz.notification.push;

import com.spacz.notification.entity.DeviceToken;
import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.PushStatus;
import com.spacz.notification.repository.DeviceTokenRepository;
import com.spacz.notification.repository.NotificationPreferenceRepository;
import com.spacz.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends the push for a saved notification, after its transaction commits, on the push executor:
 * a slow or failing FCM never delays or fails the request that created the notification.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushDispatcher {

    static final int BROADCAST_PAGE = 500;

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSender pushSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Async("pushExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(NotificationCreated event) {
        try {
            dispatch(event.notificationId());
        } catch (RuntimeException ex) {
            log.error("Push for notification {} failed", event.notificationId(), ex);
        }
    }

    void dispatch(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null || !notification.isPushRequested() || notification.getPushStatus() != PushStatus.PENDING) {
            return;
        }
        if (!pushSender.enabled()) {
            record(notificationId, PushStatus.DISABLED, PushResult.NONE);
            return;
        }
        PushMessage message = toMessage(notification);
        if (!notification.isBroadcast()) {
            sendPersonal(notification, message);
        } else {
            sendBroadcast(notification, message);
        }
    }

    private void sendPersonal(Notification notification, PushMessage message) {
        Long userId = notification.getRecipientUserId();
        if (preferenceRepository.existsByUserIdAndCategoryAndPushEnabledFalse(userId, notification.getCategory())) {
            record(notification.getId(), PushStatus.MUTED, PushResult.NONE);
            return;
        }
        List<String> tokens = deviceTokenRepository.findByUserIdOrderByLastSeenAtDesc(userId).stream()
                .map(DeviceToken::getToken).toList();
        if (tokens.isEmpty()) {
            record(notification.getId(), PushStatus.NO_DEVICES, PushResult.NONE);
            return;
        }
        finish(notification, pushSender.send(message, tokens));
    }

    /** Pages through every matching device by id, so a large audience never loads at once. */
    private void sendBroadcast(Notification notification, PushMessage message) {
        PushResult total = PushResult.NONE;
        long afterId = 0;
        int devices = 0;
        while (true) {
            List<DeviceToken> page = deviceTokenRepository.broadcastTargets(notification.getAudience().roles(),
                    notification.getCategory(), afterId, PageRequest.of(0, BROADCAST_PAGE));
            if (page.isEmpty()) {
                break;
            }
            devices += page.size();
            total = total.plus(pushSender.send(message, page.stream().map(DeviceToken::getToken).toList()));
            afterId = page.get(page.size() - 1).getId();
            if (page.size() < BROADCAST_PAGE) {
                break;
            }
        }
        if (devices == 0) {
            record(notification.getId(), PushStatus.NO_DEVICES, PushResult.NONE);
            return;
        }
        finish(notification, total);
    }

    private void finish(Notification notification, PushResult result) {
        if (!result.invalidTokens().isEmpty()) {
            Integer removed = transactionTemplate.execute(status ->
                    deviceTokenRepository.deleteByTokenIn(result.invalidTokens()));
            log.info("Removed {} device token(s) FCM no longer accepts", removed);
        }
        record(notification.getId(), result.success() > 0 ? PushStatus.SENT : PushStatus.FAILED, result);
        log.debug("Push for notification {} ({}): {} sent, {} failed", notification.getId(), notification.getType(),
                result.success(), result.failure());
    }

    private void record(Long notificationId, PushStatus status, PushResult result) {
        transactionTemplate.executeWithoutResult(tx -> notificationRepository.findById(notificationId)
                .ifPresent(n -> n.recordPush(status, result.success(), result.failure(), clock.instant())));
    }

    /** The data payload always carries the notification's identity, so tapping it can open the right screen. */
    static PushMessage toMessage(Notification n) {
        Map<String, String> data = new HashMap<>(n.getData());
        data.put("notificationId", String.valueOf(n.getId()));
        data.put("type", n.getType());
        data.put("category", n.getCategory().name());
        if (n.getEntityType() != null) {
            data.put("entityType", n.getEntityType());
        }
        if (n.getEntityId() != null) {
            data.put("entityId", String.valueOf(n.getEntityId()));
        }
        return new PushMessage(n.getTitle(), n.getBody(), n.getImageUrl(), data,
                n.getCategory().androidChannelId(), n.isHighPriority());
    }
}
