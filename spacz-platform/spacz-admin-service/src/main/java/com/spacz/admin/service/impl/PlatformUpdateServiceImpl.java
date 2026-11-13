package com.spacz.admin.service.impl;

import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.PlatformUpdateRequest;
import com.spacz.admin.dto.PlatformUpdateResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.entity.PlatformUpdate;
import com.spacz.admin.exception.ResourceNotFoundException;
import com.spacz.admin.repository.PlatformUpdateRepository;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.service.PlatformUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlatformUpdateServiceImpl implements PlatformUpdateService {

    private static final String ENTITY = "PLATFORM_UPDATE";

    private final PlatformUpdateRepository updateRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PlatformUpdateResponse> list(Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(updateRepository.findByArchivedFalse(sorted), PlatformUpdateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformUpdateResponse get(Long id) {
        return PlatformUpdateResponse.from(find(id));
    }

    @Override
    @Transactional
    public PlatformUpdateResponse create(AuthenticatedUser admin, PlatformUpdateRequest request) {
        PlatformUpdate update = PlatformUpdate.create(admin.userId());
        apply(update, request);
        update = updateRepository.saveAndFlush(update);
        auditService.record(admin, AuditAction.PLATFORM_UPDATE_CREATED, ENTITY, update.getId(),
                "Created update " + update.getTitle());
        return PlatformUpdateResponse.from(update);
    }

    @Override
    @Transactional
    public PlatformUpdateResponse update(AuthenticatedUser admin, Long id, PlatformUpdateRequest request) {
        PlatformUpdate update = find(id);
        apply(update, request);
        update = updateRepository.saveAndFlush(update);
        auditService.record(admin, AuditAction.PLATFORM_UPDATE_UPDATED, ENTITY, id, "Updated update " + update.getTitle());
        return PlatformUpdateResponse.from(update);
    }

    @Override
    @Transactional
    public PlatformUpdateResponse setPublished(AuthenticatedUser admin, Long id, boolean published) {
        PlatformUpdate update = find(id);
        if (published) {
            update.publish(clock.instant());
        } else {
            update.unpublish();
        }
        update = updateRepository.saveAndFlush(update);
        auditService.record(admin, published ? AuditAction.PLATFORM_UPDATE_PUBLISHED : AuditAction.PLATFORM_UPDATE_UNPUBLISHED,
                ENTITY, id, (published ? "Published" : "Unpublished") + " update " + update.getTitle());
        return PlatformUpdateResponse.from(update);
    }

    @Override
    @Transactional
    public void archive(AuthenticatedUser admin, Long id) {
        PlatformUpdate update = find(id);
        update.setArchived(true);
        update.unpublish();
        updateRepository.save(update);
        auditService.record(admin, AuditAction.PLATFORM_UPDATE_ARCHIVED, ENTITY, id, "Archived update " + update.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformUpdateResponse> published() {
        return updateRepository.findByPublishedTrueAndArchivedFalseOrderByPublishedAtDesc().stream()
                .map(PlatformUpdateResponse::from).toList();
    }

    private PlatformUpdate find(Long id) {
        return updateRepository.findById(id).filter(u -> !u.isArchived())
                .orElseThrow(() -> new ResourceNotFoundException("Update", id));
    }

    private void apply(PlatformUpdate update, PlatformUpdateRequest request) {
        update.setTitle(request.title().trim());
        update.setDescription(request.description().trim());
        if (Boolean.TRUE.equals(request.published())) {
            update.publish(clock.instant());
        } else if (Boolean.FALSE.equals(request.published())) {
            update.unpublish();
        }
    }
}
