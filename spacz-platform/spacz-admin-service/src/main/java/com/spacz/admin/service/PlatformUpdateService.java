package com.spacz.admin.service;

import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.dto.PlatformUpdateRequest;
import com.spacz.admin.dto.PlatformUpdateResponse;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PlatformUpdateService {

    PageResponse<PlatformUpdateResponse> list(Pageable pageable);

    PlatformUpdateResponse get(Long id);

    PlatformUpdateResponse create(AuthenticatedUser admin, PlatformUpdateRequest request);

    PlatformUpdateResponse update(AuthenticatedUser admin, Long id, PlatformUpdateRequest request);

    PlatformUpdateResponse setPublished(AuthenticatedUser admin, Long id, boolean published);

    void archive(AuthenticatedUser admin, Long id);

    /** Published, non-archived updates, newest first. */
    List<PlatformUpdateResponse> published();
}
