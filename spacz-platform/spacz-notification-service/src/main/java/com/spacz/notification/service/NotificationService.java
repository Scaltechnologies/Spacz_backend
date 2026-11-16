package com.spacz.notification.service;

import com.spacz.notification.dto.NotificationResponse;
import com.spacz.notification.dto.PageResponse;
import com.spacz.notification.dto.admin.AdminNotificationResponse;
import com.spacz.notification.dto.admin.SendNotificationRequest;
import com.spacz.notification.dto.internal.CreateNotificationRequest;
import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.entity.NotificationReceipt;
import com.spacz.notification.exception.BusinessRuleException;
import com.spacz.notification.exception.ResourceNotFoundException;
import com.spacz.notification.push.NotificationCreated;
import com.spacz.notification.repository.NotificationReceiptRepository;
import com.spacz.notification.repository.NotificationRepository;
import com.spacz.notification.repository.NotificationSpecifications;
import com.spacz.notification.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    static final int MAX_DATA_ENTRIES = 20;
    static final int MAX_DATA_KEY = 40;
    static final int MAX_DATA_VALUE = 500;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationRepository notificationRepository;
    private final NotificationReceiptRepository receiptRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record Created(Notification notification, boolean isNew) {
    }

    /**
     * Saves a notification sent by another service (or an admin). With a {@code dedupKey} it is
     * idempotent: the same key returns the notification created the first time, without a second push.
     */
    @Transactional
    public Created create(CreateNotificationRequest request, String sourceService, Long createdBy) {
        if (request.dedupKey() != null && !request.dedupKey().isBlank()) {
            Optional<Notification> existing = notificationRepository.findByDedupKey(request.dedupKey().trim());
            if (existing.isPresent()) {
                return new Created(existing.get(), false);
            }
        }
        if (request.recipientUserId() != null && request.audience() == Audience.ALL) {
            throw new BusinessRuleException("INVALID_AUDIENCE",
                    "A personal notification needs the recipient's role (USER, VENDOR or ADMIN) as audience");
        }
        Map<String, String> data = validData(request.data());
        boolean push = request.push() == null || request.push();
        Notification notification = notificationRepository.saveAndFlush(Notification.builder()
                .recipientUserId(request.recipientUserId())
                .audience(request.audience())
                .category(request.category())
                .type(request.type())
                .title(request.title().trim())
                .body(request.body().trim())
                .imageUrl(blankToNull(request.imageUrl()))
                .entityType(blankToNull(request.entityType()))
                .entityId(request.entityId())
                .data(data)
                .highPriority(Boolean.TRUE.equals(request.highPriority()))
                .sourceService(sourceService == null || sourceService.isBlank() ? "unknown" : sourceService.trim())
                .createdBy(createdBy)
                .dedupKey(blankToNull(request.dedupKey()))
                .push(push)
                .build());
        if (push) {
            events.publishEvent(new NotificationCreated(notification.getId()));
        }
        log.debug("Notification {} {} for {} {}", notification.getId(), notification.getType(),
                notification.isBroadcast() ? "audience" : "user",
                notification.isBroadcast() ? notification.getAudience() : notification.getRecipientUserId());
        return new Created(notification, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> inbox(AuthenticatedUser caller, NotificationCategory category,
                                                    boolean unreadOnly, Pageable pageable) {
        Specification<Notification> spec = Specification.where(NotificationSpecifications.inboxOf(caller.userId(), caller.role()))
                .and(NotificationSpecifications.inCategory(category));
        if (unreadOnly) {
            spec = spec.and(NotificationSpecifications.unreadBy(caller.userId()));
        }
        Page<Notification> page = notificationRepository.findAll(spec,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST));
        Set<Long> readBroadcasts = readBroadcastIds(caller.userId(), page.getContent());
        return PageResponse.from(page, n -> NotificationResponse.from(n, isRead(n, readBroadcasts)));
    }

    @Transactional(readOnly = true)
    public long unreadCount(AuthenticatedUser caller) {
        return notificationRepository.count(Specification.where(NotificationSpecifications.inboxOf(caller.userId(), caller.role()))
                .and(NotificationSpecifications.unreadBy(caller.userId())));
    }

    @Transactional(readOnly = true)
    public NotificationResponse get(AuthenticatedUser caller, Long id) {
        Notification notification = visible(caller, id);
        return NotificationResponse.from(notification, isRead(notification, readBroadcastIds(caller.userId(), List.of(notification))));
    }

    @Transactional
    public NotificationResponse markRead(AuthenticatedUser caller, Long id) {
        Notification notification = visible(caller, id);
        Instant now = clock.instant();
        if (notification.isBroadcast()) {
            if (receiptRepository.findByNotificationIdAndUserId(id, caller.userId()).isEmpty()) {
                receiptRepository.save(NotificationReceipt.read(id, caller.userId(), now));
            }
        } else {
            notification.markRead(now);
        }
        return NotificationResponse.from(notification, true);
    }

    @Transactional
    public int markAllRead(AuthenticatedUser caller) {
        Instant now = clock.instant();
        int personal = notificationRepository.markAllPersonalRead(caller.userId(), now);
        List<Long> broadcasts = notificationRepository.unreadBroadcastIds(caller.userId(), Audience.visibleTo(caller.role()));
        receiptRepository.saveAll(broadcasts.stream()
                .map(id -> NotificationReceipt.read(id, caller.userId(), now)).toList());
        return personal + broadcasts.size();
    }

    /** Personal notifications are deleted; a broadcast is only hidden for the caller. */
    @Transactional
    public void dismiss(AuthenticatedUser caller, Long id) {
        Notification notification = visible(caller, id);
        if (!notification.isBroadcast()) {
            notificationRepository.delete(notification);
            return;
        }
        Instant now = clock.instant();
        NotificationReceipt receipt = receiptRepository.findByNotificationIdAndUserId(id, caller.userId())
                .orElseGet(() -> NotificationReceipt.read(id, caller.userId(), now));
        receipt.dismiss(now);
        receiptRepository.save(receipt);
    }

    /** One broadcast for an audience, or one personal notification per listed account. */
    @Transactional
    public List<AdminNotificationResponse> send(AuthenticatedUser admin, SendNotificationRequest request) {
        NotificationCategory category = request.category() != null ? request.category() : NotificationCategory.ANNOUNCEMENT;
        String type = "ADMIN_MESSAGE";
        List<AdminNotificationResponse> sent = new ArrayList<>();
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            Audience role = request.audience() == null || request.audience() == Audience.ALL ? Audience.USER : request.audience();
            for (Long userId : new LinkedHashSet<>(request.userIds())) {
                sent.add(AdminNotificationResponse.from(create(new CreateNotificationRequest(userId, role, category, type,
                        request.title(), request.body(), request.imageUrl(), null, null, request.data(), request.push(),
                        request.highPriority(), null), "admin-panel", admin.userId()).notification()));
            }
        } else {
            sent.add(AdminNotificationResponse.from(create(new CreateNotificationRequest(null, request.audience(), category,
                    type, request.title(), request.body(), request.imageUrl(), null, null, request.data(), request.push(),
                    request.highPriority(), null), "admin-panel", admin.userId()).notification()));
        }
        log.info("Admin {} sent '{}' ({} notification(s))", admin.userId(), request.title(), sent.size());
        return sent;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> sentByAdmins(Pageable pageable) {
        return PageResponse.from(notificationRepository.findByCreatedByIsNotNull(
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST)), AdminNotificationResponse::from);
    }

    /** 404 (not 403) for notifications of other accounts, so IDs reveal nothing. */
    private Notification visible(AuthenticatedUser caller, Long id) {
        return notificationRepository.findOne(Specification.where(NotificationSpecifications.inboxOf(caller.userId(), caller.role()))
                        .and((root, query, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
    }

    private Set<Long> readBroadcastIds(Long userId, List<Notification> notifications) {
        List<Long> broadcastIds = notifications.stream().filter(Notification::isBroadcast).map(Notification::getId).toList();
        if (broadcastIds.isEmpty()) {
            return Set.of();
        }
        return receiptRepository.findByUserIdAndNotificationIdIn(userId, broadcastIds).stream()
                .map(NotificationReceipt::getNotificationId).collect(Collectors.toSet());
    }

    private static boolean isRead(Notification n, Set<Long> readBroadcasts) {
        return n.isBroadcast() ? readBroadcasts.contains(n.getId()) : n.getReadAt() != null;
    }

    /** FCM data must be string → string; keep it small (the whole FCM payload is limited to 4 KB). */
    static Map<String, String> validData(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        if (data.size() > MAX_DATA_ENTRIES) {
            throw new BusinessRuleException("INVALID_DATA", "data can have at most " + MAX_DATA_ENTRIES + " entries");
        }
        data.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_DATA_KEY) {
                throw new BusinessRuleException("INVALID_DATA", "data keys must be 1-" + MAX_DATA_KEY + " characters");
            }
            if (value == null || value.length() > MAX_DATA_VALUE) {
                throw new BusinessRuleException("INVALID_DATA",
                        "data value of '" + key + "' must be present and at most " + MAX_DATA_VALUE + " characters");
            }
        });
        return data;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
