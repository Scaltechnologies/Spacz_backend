package com.spacz.admin.service.impl;

import com.spacz.admin.dto.OfferRequest;
import com.spacz.admin.dto.OfferResponse;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.entity.AuditAction;
import com.spacz.admin.entity.Offer;
import com.spacz.admin.exception.ResourceNotFoundException;
import com.spacz.admin.repository.OfferRepository;
import com.spacz.admin.security.AuthenticatedUser;
import com.spacz.admin.service.AuditService;
import com.spacz.admin.service.OfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OfferServiceImpl implements OfferService {

    private static final String ENTITY = "OFFER";
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("createdAt"));

    private final OfferRepository offerRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OfferResponse> list(Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(offerRepository.findByArchivedFalse(sorted), o -> OfferResponse.from(o, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public OfferResponse get(Long id) {
        return OfferResponse.from(find(id), clock.instant());
    }

    @Override
    @Transactional
    public OfferResponse create(AuthenticatedUser admin, OfferRequest request) {
        Offer offer = Offer.create(admin.userId());
        apply(offer, request);
        offer = offerRepository.saveAndFlush(offer);
        auditService.record(admin, AuditAction.OFFER_CREATED, ENTITY, offer.getId(), "Created offer " + offer.getTitle());
        return OfferResponse.from(offer, clock.instant());
    }

    @Override
    @Transactional
    public OfferResponse update(AuthenticatedUser admin, Long id, OfferRequest request) {
        Offer offer = find(id);
        apply(offer, request);
        offer = offerRepository.saveAndFlush(offer);
        auditService.record(admin, AuditAction.OFFER_UPDATED, ENTITY, id, "Updated offer " + offer.getTitle());
        return OfferResponse.from(offer, clock.instant());
    }

    @Override
    @Transactional
    public OfferResponse setActive(AuthenticatedUser admin, Long id, boolean active) {
        Offer offer = find(id);
        offer.setActive(active);
        offer = offerRepository.saveAndFlush(offer);
        auditService.record(admin, active ? AuditAction.OFFER_ACTIVATED : AuditAction.OFFER_DEACTIVATED, ENTITY, id,
                (active ? "Enabled" : "Disabled") + " offer " + offer.getTitle());
        return OfferResponse.from(offer, clock.instant());
    }

    @Override
    @Transactional
    public void archive(AuthenticatedUser admin, Long id) {
        Offer offer = find(id);
        offer.setArchived(true);
        offer.setActive(false);
        offerRepository.save(offer);
        auditService.record(admin, AuditAction.OFFER_ARCHIVED, ENTITY, id, "Archived offer " + offer.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfferResponse> live() {
        var now = clock.instant();
        return offerRepository.findLive(now).stream().map(o -> OfferResponse.from(o, now)).toList();
    }

    private Offer find(Long id) {
        return offerRepository.findById(id).filter(o -> !o.isArchived())
                .orElseThrow(() -> new ResourceNotFoundException("Offer", id));
    }

    private static void apply(Offer offer, OfferRequest request) {
        offer.setTitle(request.title().trim());
        offer.setDescription(blankToNull(request.description()));
        offer.setImageUrl(blankToNull(request.imageUrl()));
        offer.setDiscountType(request.discountType());
        offer.setDiscountValue(request.discountValue());
        offer.setMaxDiscount(request.maxDiscount());
        offer.setMinBookingAmount(request.minBookingAmount());
        offer.setApplicablePlan(request.applicablePlan() != null ? request.applicablePlan() : Offer.ApplicablePlan.ANY);
        offer.setFirstBookingOnly(Boolean.TRUE.equals(request.firstBookingOnly()));
        offer.setTerms(blankToNull(request.terms()));
        offer.setStartAt(request.startAt());
        offer.setEndAt(request.endAt());
        offer.setActive(request.active() == null || request.active());
        offer.setPriority(request.priority() != null ? request.priority() : 0);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
