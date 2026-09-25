package com.spacz.admin.repository;

import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> matching(String search, AuditAction action, Long actorUserId, String actorRole,
                                                   String entityType, Long entityId, Instant from, Instant to) {
        Specification<AuditLog> spec = Specification.where(null);
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("description")), pattern),
                    cb.like(cb.lower(root.get("actorEmail")), pattern)));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (actorUserId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorUserId"), actorUserId));
        }
        if (StringUtils.hasText(actorRole)) {
            String role = actorRole.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actorRole"), role));
        }
        if (StringUtils.hasText(entityType)) {
            String type = entityType.trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityType"), type));
        }
        if (entityId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("entityId"), entityId));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }
        return spec;
    }
}
