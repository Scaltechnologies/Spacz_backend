package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSearchCriteria;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.seat.AvailabilityResponse;
import com.spacz.studyhall.dto.seat.BlockResponse;
import com.spacz.studyhall.dto.seat.SeatMapResponse;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.mapper.SeatMapper;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.StudyHallSpecifications;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.StudyHallQueryService;
import com.spacz.studyhall.service.support.PageableSupport;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudyHallQueryServiceImpl implements StudyHallQueryService {

    private static final Set<String> SORTABLE = Set.of("name", "city", "pricePerDay", "pricePerMonth", "createdAt");
    private static final double DEFAULT_RADIUS_KM = 10.0;
    private static final int MAX_RANGE_DAYS = 366;

    private final StudyHallRepository studyHallRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final VendorAccess vendorAccess;
    private final HallViewAssembler assembler;
    private final SeatMapper seatMapper;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudyHallSummaryResponse> search(StudyHallSearchCriteria c, Pageable pageable) {
        if ((c.availableFrom() == null) != (c.availableTo() == null)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "availableFrom and availableTo must be given together");
        }
        if (c.availableFrom() != null) {
            validateRange(c.availableFrom(), c.availableTo());
        }
        if (c.minPrice() != null && c.maxPrice() != null && c.minPrice().compareTo(c.maxPrice()) > 0) {
            throw new BusinessRuleException("INVALID_PRICE_RANGE", "minPrice must not exceed maxPrice");
        }
        Double radius = c.latitude() != null && c.longitude() != null
                ? (c.radiusKm() != null ? c.radiusKm() : DEFAULT_RADIUS_KM) : null;

        Specification<StudyHall> spec = Specification.where(StudyHallSpecifications.publiclyVisible())
                .and(StudyHallSpecifications.textMatches(c.search()))
                .and(StudyHallSpecifications.inCity(c.city()))
                .and(StudyHallSpecifications.supportsProgram(c.programId()))
                .and(StudyHallSpecifications.hasAllAmenities(c.amenityIds()))
                .and(StudyHallSpecifications.priceBetween(c.minPrice(), c.maxPrice()))
                .and(StudyHallSpecifications.withinRadius(c.latitude(), c.longitude(), radius))
                .and(StudyHallSpecifications.hasFreeSeat(c.availableFrom(), c.availableTo(), clock.instant()));

        Pageable safe = PageableSupport.restrictSort(pageable, SORTABLE, Sort.by("name"));
        return assembler.page(studyHallRepository.findAll(spec, safe), c.latitude(), c.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public StudyHallDetailResponse get(Long studyHallId, AuthenticatedUser caller) {
        return assembler.detail(vendorAccess.viewableHall(studyHallId, caller));
    }

    @Override
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(Long studyHallId, AuthenticatedUser caller, LocalDate startDate, LocalDate endDate) {
        StudyHall hall = vendorAccess.viewableHall(studyHallId, caller);
        LocalDate start = startDate != null ? startDate : today();
        LocalDate end = endDate != null ? endDate : start;
        validateRange(start, end);
        Set<Long> occupied = bookingRepository.findOccupiedSeatIds(studyHallId, start, end, clock.instant());
        List<BlockResponse> blocks = hall.getBlocks().stream()
                .map(block -> seatMapper.toResponse(block, occupied))
                .toList();
        return new SeatMapResponse(studyHallId, start, end, blocks);
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityResponse availability(Long studyHallId, AuthenticatedUser caller, LocalDate startDate,
                                             LocalDate endDate) {
        vendorAccess.viewableHall(studyHallId, caller);
        LocalDate start = startDate != null ? startDate : today();
        LocalDate end = endDate != null ? endDate : start;
        validateRange(start, end);
        List<Seat> seats = seatRepository.findByStudyHallId(studyHallId);
        Set<Long> occupied = bookingRepository.findOccupiedSeatIds(studyHallId, start, end, clock.instant());
        List<Long> available = seats.stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE && !occupied.contains(s.getId()))
                .map(Seat::getId)
                .sorted()
                .toList();
        long outOfService = seats.stream().filter(s -> s.getStatus() != SeatStatus.AVAILABLE).count();
        long occupiedInService = seats.size() - outOfService - available.size();
        return new AvailabilityResponse(studyHallId, start, end, seats.size(), available.size(), occupiedInService,
                outOfService, available);
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "End date must not be before start date");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Date range must not exceed " + MAX_RANGE_DAYS + " days");
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(bookingProperties.zone()));
    }
}
