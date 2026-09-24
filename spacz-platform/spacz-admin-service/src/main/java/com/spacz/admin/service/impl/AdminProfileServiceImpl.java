package com.spacz.admin.service.impl;

import com.spacz.admin.dto.AdminProfileRequest;
import com.spacz.admin.dto.AdminProfileResponse;
import com.spacz.admin.entity.AdminProfile;
import com.spacz.admin.mapper.AuditLogMapper;
import com.spacz.admin.repository.AdminProfileRepository;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AdminProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AdminProfileServiceImpl implements AdminProfileService {

    private final AdminProfileRepository repository;
    private final AuditLogMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public AdminProfileResponse me(AuthenticatedUser admin) {
        AdminProfile profile = findOrCreate(admin);
        profile.setLastSeenAt(clock.instant());
        return mapper.toResponse(repository.saveAndFlush(profile));
    }

    @Override
    @Transactional
    public AdminProfileResponse update(AuthenticatedUser admin, AdminProfileRequest request) {
        AdminProfile profile = findOrCreate(admin);
        profile.setFullName(request.fullName().trim());
        if (request.email() != null && !request.email().isBlank()) {
            profile.setEmail(request.email().trim());
        }
        profile.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        profile.setTitle(request.title() == null || request.title().isBlank() ? null : request.title().trim());
        return mapper.toResponse(repository.saveAndFlush(profile));
    }

    private AdminProfile findOrCreate(AuthenticatedUser admin) {
        return repository.findByAdminUserId(admin.userId())
                .orElseGet(() -> repository.save(AdminProfile.create(admin.userId(), admin.email())));
    }
}
