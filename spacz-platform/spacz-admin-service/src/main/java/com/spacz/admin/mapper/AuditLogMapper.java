package com.spacz.admin.mapper;

import com.spacz.admin.dto.AdminProfileResponse;
import com.spacz.admin.dto.AuditLogResponse;
import com.spacz.admin.entity.AdminProfile;
import com.spacz.admin.entity.AuditLog;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUserId(), log.getActorEmail(), log.getActorRole(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getDescription(), log.getIpAddress(),
                log.getUserAgent(), log.getCorrelationId(), log.getSourceService(), log.getCreatedAt());
    }

    public AdminProfileResponse toResponse(AdminProfile p) {
        return new AdminProfileResponse(p.getId(), p.getAdminUserId(), p.getFullName(), p.getEmail(), p.getPhone(),
                p.getTitle(), p.getLastSeenAt(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
