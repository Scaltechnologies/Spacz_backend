package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.client.dto.UserSummaryDto;
import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.BookingQuoteResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.booking.VendorBookingResponse;
import com.spacz.studyhall.dto.enrollment.StudentInfo;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.Program;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.history.ActorType;
import com.spacz.studyhall.history.BookingStatusChangedEvent;
import com.spacz.studyhall.mapper.BookingMapper;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.BookingSpecifications;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.support.PageableSupport;
import com.spacz.studyhall.service.support.PriceBreakdown;
import com.spacz.studyhall.service.support.VendorAccess;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "startDate", "endDate", "status", "totalPrice");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final char[] REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;
    private final SeatRepository seatRepository;
    private final ProgramRepository programRepository;
    private final VendorAccess vendorAccess;
    private final EnrollmentService enrollmentService;
    private final UserServiceClient userServiceClient;
    private final BookingMapper mapper;
    private final BookingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BookingPricing pricing;

    /** Field-injected (not a constructor argument) so unit tests can build the service positionally. */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Double-booking prevention, in order:
     * <ol>
     *   <li>{@code SELECT ... FOR UPDATE} on the seat: concurrent requests for this seat queue here</li>
     *   <li>stale PENDING holds on the seat are expired</li>
     *   <li>overlap check against PENDING/CONFIRMED bookings</li>
     *   <li>PostgreSQL exclusion constraint on (seat_id, daterange) as the last line of defence</li>
     * </ol>
     * The price is always computed here (seat → block → hall, then {@link BookingPricing}), never taken from the client.
     */
    @Override
    @Transactional
    public BookingResponse create(Long userId, CreateBookingRequest request) {
        Instant now = clock.instant();
        LocalDate today = today();
        BookingPlan plan = request.effectivePlan();
        LocalDate start = request.startDate();
        LocalDate end = request.effectiveEndDate();
        validateDates(plan, start, end, today);

        Seat seat = seatRepository.findByIdForUpdate(request.seatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", request.seatId()));
        StudyHall hall = seat.getStudyHall();
        BigDecimal unitPrice = bookableUnitPrice(seat, request.studyHallId(), plan);
        Program program = null;
        if (request.programId() != null) {
            program = programRepository.findById(request.programId()).filter(Program::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Program", request.programId()));
        }

        expireStaleHolds(seat.getId(), now);
        checkAvailability(userId, seat, start, end, now);

        Booking booking = Booking.hold(newReference(), userId, seat, program, plan, start, end, unitPrice,
                now.plus(properties.holdDuration()));
        booking.applyPricing(pricing.price(userId, plan, start, end, unitPrice, now));
        if (properties.autoConfirm()) {
            // Dev/demo only: bypasses payment entirely. BOOKING_AUTO_CONFIRM must be false wherever payments are used.
            booking.confirm(now);
        }
        try {
            booking = bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            // Exclusion constraint: another transaction booked this seat for overlapping dates.
            throw new ConflictException("SEAT_UNAVAILABLE",
                    "Seat " + seat.getSeatNumber() + " is already booked for some of the selected dates");
        }
        log.info("Booking {} created: user {} seat {} {} {}..{} ({})", booking.getBookingReference(), userId,
                seat.getId(), plan, start, end, booking.getStatus());
        recordStatus(booking, null, ActorType.USER, userId, null,
                booking.getStatus() == BookingStatus.CONFIRMED ? "Booked (auto-confirmed, no payment)" : "Booked; seat held");
        publish(booking, "BOOKING_CREATED", "Booked seat " + seat.getSeatNumber() + " at " + hall.getName()
                + " from " + start + " to " + end);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            enrollmentService.recordConfirmedBooking(booking);
            publish(booking, "BOOKING_CONFIRMED", "Booking " + booking.getBookingReference() + " confirmed");
        }
        return mapper.toResponse(booking);
    }

    /** Same validation and pricing as {@link #create}, without holding the seat. */
    @Override
    @Transactional(readOnly = true)
    public BookingQuoteResponse quote(Long userId, CreateBookingRequest request) {
        Instant now = clock.instant();
        BookingPlan plan = request.effectivePlan();
        LocalDate start = request.startDate();
        LocalDate end = request.effectiveEndDate();
        validateDates(plan, start, end, today());

        Seat seat = seatRepository.findById(request.seatId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", request.seatId()));
        StudyHall hall = seat.getStudyHall();
        BigDecimal unitPrice = bookableUnitPrice(seat, request.studyHallId(), plan);
        checkAvailability(userId, seat, start, end, now);

        PriceBreakdown price = pricing.price(userId, plan, start, end, unitPrice, now);
        return new BookingQuoteResponse(hall.getId(), hall.getName(), seat.getId(), seat.getSeatNumber(), plan, start,
                end, price.units(), price.unitPrice(), price.baseAmount(), price.discountAmount(), price.offerId(),
                price.offerTitle(), price.platformCharges(), price.taxes(), price.total());
    }

    /** The seat must belong to the hall, the hall and seat must be bookable online, and the plan offered. */
    private BigDecimal bookableUnitPrice(Seat seat, Long studyHallId, BookingPlan plan) {
        StudyHall hall = seat.getStudyHall();
        if (!hall.getId().equals(studyHallId)) {
            throw new ResourceNotFoundException("Seat " + seat.getId() + " not found in study hall " + studyHallId);
        }
        if (!hall.isBookable()) {
            throw new BusinessRuleException("STUDY_HALL_NOT_BOOKABLE", "This study hall is not accepting bookings");
        }
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new BusinessRuleException("SEAT_NOT_BOOKABLE", "Seat " + seat.getSeatNumber() + " is not available online");
        }
        BigDecimal unitPrice = seat.effectivePrice(plan);
        if (unitPrice == null) {
            throw new BusinessRuleException("PLAN_NOT_OFFERED",
                    plan + " bookings are not offered for seat " + seat.getSeatNumber());
        }
        return unitPrice;
    }

    private void checkAvailability(Long userId, Seat seat, LocalDate start, LocalDate end, Instant now) {
        if (bookingRepository.existsOverlapping(seat.getId(), start, end, BookingStatus.OCCUPYING)) {
            throw new ConflictException("SEAT_UNAVAILABLE",
                    "Seat " + seat.getSeatNumber() + " is already booked for some of the selected dates");
        }
        if (bookingRepository.existsUserOverlap(userId, seat.getStudyHall().getId(), start, end, now)) {
            throw new ConflictException("DUPLICATE_BOOKING",
                    "You already have a booking in this study hall for some of these dates");
        }
    }

    @Override
    @Transactional
    public BookingResponse confirmByAdmin(Long adminId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        BookingStatus previous = booking.getStatus();
        booking.confirm(clock.instant());
        bookingRepository.saveAndFlush(booking);
        String note = reason == null || reason.isBlank() ? "" : ": " + reason.strip();
        recordStatus(booking, previous, ActorType.ADMIN, adminId, null,
                "Confirmed manually by an admin (no in-app payment)" + note);
        enrollmentService.recordConfirmedBooking(booking);
        log.info("Booking {} confirmed manually by an admin (no in-app payment)", booking.getBookingReference());
        publish(booking, "BOOKING_CONFIRMED", "Booking " + booking.getBookingReference() + " confirmed");
        return mapper.toResponse(booking);
    }

    /**
     * Never catches DataIntegrityViolationException: after a failed flush the session is unusable. The
     * seat is checked first, under the same seat row lock booking creation takes, so the exclusion
     * constraint cannot fire here.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public PaidConfirmation confirmAfterPayment(Long bookingId, Long paymentId, ActorType actorType, Long actorId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        Seat seat = seatRepository.findByIdForUpdate(booking.getSeat().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seat", booking.getSeat().getId()));
        // Re-read under the seat lock: the lifecycle job may have expired the hold meanwhile.
        entityManager.flush(); // refresh discards unflushed changes
        entityManager.refresh(booking);
        Instant now = clock.instant();
        switch (booking.getStatus()) {
            case CONFIRMED, COMPLETED -> {
                return PaidConfirmation.ALREADY_CONFIRMED;
            }
            case CANCELLED -> {
                return PaidConfirmation.SEAT_LOST;
            }
            case EXPIRED -> {
                // Release other stale holds first (as booking creation does), then the seat and the student's
                // slot in this hall must still be free for these dates.
                expireStaleHolds(seat.getId(), now);
                if (bookingRepository.existsOverlapping(seat.getId(), booking.getStartDate(), booking.getEndDate(),
                        BookingStatus.OCCUPYING)
                        || bookingRepository.existsUserOverlap(booking.getUserId(), booking.getStudyHall().getId(),
                        booking.getStartDate(), booking.getEndDate(), now)) {
                    return PaidConfirmation.SEAT_LOST;
                }
            }
            default -> {
                // PENDING (even past its hold): its row still occupies the seat, nobody else could book it.
            }
        }
        BookingStatus previous = booking.getStatus();
        booking.confirmAfterPayment(now);
        bookingRepository.saveAndFlush(booking);
        recordStatus(booking, previous, actorType, actorId, paymentId, actorType == ActorType.VENDOR
                ? "Payment collected at the hall and confirmed by the vendor" : "Payment received");
        enrollmentService.recordConfirmedBooking(booking);
        publish(booking, "BOOKING_CONFIRMED", "Booking " + booking.getBookingReference() + " confirmed by payment");
        return PaidConfirmation.CONFIRMED;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void extendHoldForPayment(Long bookingId, Instant until) {
        bookingRepository.findById(bookingId).ifPresent(booking -> booking.extendHold(until));
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId, String reason) {
        Booking booking = ownBooking(userId, bookingId);
        BookingStatus previous = booking.getStatus();
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        booking.cancel(cleanReason, clock.instant(), today());
        bookingRepository.saveAndFlush(booking);
        recordStatus(booking, previous, ActorType.USER, userId, null,
                cleanReason == null ? "Cancelled by the student" : "Cancelled by the student: " + cleanReason);
        publish(booking, "BOOKING_CANCELLED", "Booking " + booking.getBookingReference() + " cancelled");
        return mapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> myBookings(Long userId, BookingStatus status, Pageable pageable) {
        Pageable safe = PageableSupport.restrictSort(pageable, SORTABLE, DEFAULT_SORT);
        Page<Booking> page = status == null
                ? bookingRepository.findByUserId(userId, safe)
                : bookingRepository.findByUserIdAndStatus(userId, status, safe);
        return PageResponse.from(page, mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse get(AuthenticatedUser caller, Long bookingId) {
        Booking booking = bookingRepository.findDetailed(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        boolean allowed = caller.isAdmin()
                || (caller.isUser() && booking.getUserId().equals(caller.userId()))
                || (caller.isVendor() && booking.getStudyHall().isOwnedBy(caller.userId()));
        if (!allowed) {
            throw new ResourceNotFoundException("Booking", bookingId);
        }
        return mapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VendorBookingResponse> vendorBookings(Long vendorId, Long studyHallId, BookingStatus status,
                                                             LocalDate from, LocalDate to, Pageable pageable) {
        if (studyHallId != null) {
            vendorAccess.ownedHall(vendorId, studyHallId);
        } else {
            vendorAccess.vendor(vendorId);
        }
        Specification<Booking> spec = Specification.where(BookingSpecifications.fetchHallAndSeat())
                .and(BookingSpecifications.forVendor(vendorId))
                .and(BookingSpecifications.forHall(studyHallId))
                .and(BookingSpecifications.withStatus(status))
                .and(BookingSpecifications.overlapping(from, to));
        Page<Booking> page = bookingRepository.findAll(spec, PageableSupport.restrictSort(pageable, SORTABLE, DEFAULT_SORT));
        Map<Long, UserSummaryDto> students = lookupStudents(page.getContent().stream().map(Booking::getUserId)
                .collect(Collectors.toSet()));
        return PageResponse.from(page, booking -> {
            UserSummaryDto s = students.get(booking.getUserId());
            return new VendorBookingResponse(mapper.toResponse(booking), s == null ? null
                    : new StudentInfo(s.userId(), fullName(s), s.phone(), s.email(), true));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> search(Long studyHallId, Long userId, BookingStatus status, LocalDate from,
                                                LocalDate to, Pageable pageable) {
        Specification<Booking> spec = Specification.where(BookingSpecifications.fetchHallAndSeat())
                .and(BookingSpecifications.forHall(studyHallId))
                .and(BookingSpecifications.forUser(userId))
                .and(BookingSpecifications.withStatus(status))
                .and(BookingSpecifications.overlapping(from, to));
        return PageResponse.from(bookingRepository.findAll(spec,
                PageableSupport.restrictSort(pageable, SORTABLE, DEFAULT_SORT)), mapper::toResponse);
    }

    @Override
    @Transactional
    public int expireHolds(int batchSize) {
        List<Booking> expired = bookingRepository.findExpiredHolds(BookingStatus.PENDING, clock.instant(),
                PageRequest.of(0, batchSize));
        expired.forEach(booking -> {
            booking.expire();
            recordStatus(booking, BookingStatus.PENDING, ActorType.SYSTEM, null, null, "Seat hold expired without payment");
            publish(booking, "BOOKING_EXPIRED", "Seat hold for booking " + booking.getBookingReference() + " expired");
        });
        return expired.size();
    }

    @Override
    @Transactional
    public int completeFinished(int batchSize) {
        List<Booking> finished = bookingRepository.findFinished(BookingStatus.CONFIRMED, today(), PageRequest.of(0, batchSize));
        finished.forEach(booking -> {
            booking.complete();
            recordStatus(booking, BookingStatus.CONFIRMED, ActorType.SYSTEM, null, null, "Booking period ended");
            publish(booking, "BOOKING_COMPLETED", "Booking " + booking.getBookingReference() + " completed");
        });
        return finished.size();
    }

    /**
     * Releases PENDING holds on this seat whose hold time has passed. Caller holds the seat row lock.
     * Row by row with a guarded update (not one bulk update), so each booking that really changed here, and
     * only those, gets a history row: if the lifecycle job expired one meanwhile, the guarded update matches
     * nothing and that job records it instead.
     */
    private void expireStaleHolds(Long seatId, Instant now) {
        for (Long id : bookingRepository.findStaleHoldIds(seatId, now, BookingStatus.PENDING)) {
            if (bookingRepository.expireIfStale(id, now, BookingStatus.PENDING, BookingStatus.EXPIRED) == 1) {
                events.publishEvent(BookingStatusChangedEvent.of(id, BookingStatus.PENDING, BookingStatus.EXPIRED,
                        ActorType.SYSTEM, null, "Seat hold expired without payment"));
            }
        }
    }

    private void recordStatus(Booking booking, BookingStatus previous, ActorType actorType, Long actorId,
                              Long paymentId, String reason) {
        events.publishEvent(new BookingStatusChangedEvent(booking.getId(), previous, booking.getStatus(), actorType,
                actorId, paymentId, reason));
    }

    private Booking ownBooking(Long userId, Long bookingId) {
        return bookingRepository.findDetailed(bookingId)
                .filter(b -> b.getUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
    }

    private void validateDates(BookingPlan plan, LocalDate start, LocalDate end, LocalDate today) {
        if (start.isBefore(today)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "Bookings cannot start in the past");
        }
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_DATE_RANGE", "End date must not be before start date");
        }
        if (plan == BookingPlan.DAILY && Booking.daysBetween(start, end) > properties.maxDays()) {
            throw new BusinessRuleException("INVALID_DATE_RANGE",
                    "A daily booking can be at most " + properties.maxDays() + " days; use a MONTHLY plan");
        }
        if (start.isAfter(today.plusDays(properties.maxAdvanceDays()))) {
            throw new BusinessRuleException("INVALID_DATE_RANGE",
                    "Bookings can start at most " + properties.maxAdvanceDays() + " days in advance");
        }
    }

    Map<Long, UserSummaryDto> lookupStudents(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userServiceClient.findUsers(userIds).stream()
                    .collect(Collectors.toMap(UserSummaryDto::userId, Function.identity(), (a, b) -> a));
        } catch (SpaczException ex) {
            log.warn("Student details unavailable: {}", ex.getMessage());
            return Map.of();
        }
    }

    static String fullName(UserSummaryDto s) {
        return s.lastName() == null ? s.firstName() : s.firstName() + " " + s.lastName();
    }

    private void publish(Booking booking, String type, String description) {
        events.publishEvent(new BookingActivityEvent(booking.getUserId(), type, description, booking.getId()));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }

    static String newReference() {
        StringBuilder reference = new StringBuilder("SPZ-");
        for (int i = 0; i < 10; i++) {
            reference.append(REFERENCE_ALPHABET[RANDOM.nextInt(REFERENCE_ALPHABET.length)]);
        }
        return reference.toString();
    }
}
