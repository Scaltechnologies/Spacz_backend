package com.spacz.admin.service.impl;

import com.spacz.admin.config.CorrelationIdFilter;
import com.spacz.admin.dto.AuditEventRequest;
import com.spacz.admin.dto.AuditLogResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.entity.AuditLog;
import com.spacz.admin.mapper.AuditLogMapper;
import com.spacz.admin.repository.AuditLogRepository;
import com.spacz.admin.repository.AuditLogSpecifications;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private static final String SERVICE_HEADER = "X-Source-Service";

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public AuditLogResponse record(AuthenticatedUser actor, AuditAction action, String entityType, Long entityId,
                                   String description) {
        HttpServletRequest request = currentRequest();
        AuditLog log = AuditLog.builder()
                .actorUserId(actor.userId())
                .actorEmail(actor.email())
                .actorRole(actor.role().name())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(truncate(description, 1000))
                .ipAddress(request != null ? truncate(request.getRemoteAddr(), 64) : null)
                .userAgent(request != null ? truncate(request.getHeader("User-Agent"), 300) : null)
                .correlationId(MDC.get(CorrelationIdFilter.MDC_KEY))
                .sourceService("admin-service")
                .createdAt(clock.instant())
                .build();
        return mapper.toResponse(repository.save(log));
    }

    @Override
    @Transactional
    public AuditLogResponse recordEvent(AuditEventRequest event) {
        HttpServletRequest request = currentRequest();
        AuditLog log = AuditLog.builder()
                .actorUserId(event.actorUserId())
                .actorRole(event.actorRole())
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .description(truncate(event.description(), 1000))
                .ipAddress(event.ipAddress())
                .correlationId(event.correlationId())
                .sourceService(request != null ? truncate(request.getHeader(SERVICE_HEADER), 40) : null)
                .createdAt(clock.instant())
                .build();
        return mapper.toResponse(repository.save(log));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(String search, AuditAction action, Long actorUserId, String actorRole,
                                                 String entityType, Long entityId, Instant from, Instant to,
                                                 Pageable pageable) {
        return PageResponse.from(repository.findAll(AuditLogSpecifications.matching(search, action, actorUserId,
                actorRole, entityType, entityId, from, to), pageable), mapper::toResponse);
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
