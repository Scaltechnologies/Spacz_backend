package com.spacz.admin.service;

import com.spacz.admin.dto.OfferRequest;
import com.spacz.admin.dto.OfferResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OfferService {

    PageResponse<OfferResponse> list(Pageable pageable);

    OfferResponse get(Long id);

    OfferResponse create(AuthenticatedUser admin, OfferRequest request);

    OfferResponse update(AuthenticatedUser admin, Long id, OfferRequest request);

    OfferResponse setActive(AuthenticatedUser admin, Long id, boolean active);

    void archive(AuthenticatedUser admin, Long id);

    /** Offers live right now, highest priority first. */
    List<OfferResponse> live();
}
