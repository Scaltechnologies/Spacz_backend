package com.spacz.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable record of an administrative action or domain event. {@code actorUserId} and
 * {@code entityId} are IDs from other services (never foreign keys); the actor is null for system events.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_email", length = 254, updatable = false)
    private String actorEmail;

    @Column(name = "actor_role", nullable = false, length = 20, updatable = false)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, length = 40, updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(length = 1000, updatable = false)
    private String description;

    @Column(name = "ip_address", length = 64, updatable = false)
    private String ipAddress;

    @Column(name = "user_agent", length = 300, updatable = false)
    private String userAgent;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    /** Service that reported the event (admin-service for admin actions). */
    @Column(name = "source_service", length = 40, updatable = false)
    private String sourceService;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
