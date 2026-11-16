package com.spacz.notification.repository;

import com.spacz.notification.entity.Audience;
import com.spacz.notification.entity.Notification;
import com.spacz.notification.entity.NotificationCategory;
import com.spacz.notification.entity.NotificationReceipt;
import com.spacz.notification.security.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Inbox filters. An account sees its personal notifications plus the broadcasts to its role (and
 * to ALL) that it has not dismissed.
 */
public final class NotificationSpecifications {

    private NotificationSpecifications() {
    }

    public static Specification<Notification> inboxOf(Long userId, Role role) {
        return (root, query, cb) -> {
            Predicate personal = cb.equal(root.get("recipientUserId"), userId);
            Predicate broadcast = cb.and(cb.isNull(root.get("recipientUserId")),
                    root.get("audience").in(Audience.visibleTo(role)),
                    cb.not(cb.exists(receipt(root, query, cb, userId, true))));
            return cb.or(personal, broadcast);
        };
    }

    /** Personal: {@code readAt} is null. Broadcast: the account has no receipt for it. */
    public static Specification<Notification> unreadBy(Long userId) {
        return (root, query, cb) -> cb.or(
                cb.and(cb.equal(root.get("recipientUserId"), userId), cb.isNull(root.get("readAt"))),
                cb.and(cb.isNull(root.get("recipientUserId")),
                        cb.not(cb.exists(receipt(root, query, cb, userId, false)))));
    }

    public static Specification<Notification> inCategory(NotificationCategory category) {
        return category == null ? null : (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    /** The account's receipt for the notification (only a dismissing one if {@code dismissedOnly}). */
    private static Subquery<Long> receipt(Root<Notification> notification, CriteriaQuery<?> query, CriteriaBuilder cb,
                                          Long userId, boolean dismissedOnly) {
        Subquery<Long> subquery = query.subquery(Long.class);
        Root<NotificationReceipt> receipt = subquery.from(NotificationReceipt.class);
        Predicate match = cb.and(cb.equal(receipt.get("notificationId"), notification.get("id")),
                cb.equal(receipt.get("userId"), userId));
        if (dismissedOnly) {
            match = cb.and(match, cb.isNotNull(receipt.get("dismissedAt")));
        }
        return subquery.select(receipt.get("id")).where(match);
    }
}
