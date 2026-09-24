package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.client.dto.UserSummaryDto;
import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
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
import com.spacz.studyhall.mapper.BookingMapper;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.BookingSpecifications;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.support.PageableSupport;
import com.spacz.studyhall.service.support.VendorAccess;
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

    /**
     * Double-booking prevention, in order:
     * <ol>
     *   <li>{@code SELECT ... FOR UPDATE} on the seat: concurrent requests for this seat queue here</li>
     *   <li>stale PENDING holds on the seat are expired</li>
     *   <li>overlap check against PENDING/CONFIRMED bookings</li>
     *   <li>PostgreSQL exclusion constraint on (seat_id, daterange) as the last line of defence</li>
     * </ol>
     * The price is always computed here (seat → block → hall), never taken from the client.
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
        if (!hall.getId().equals(request.studyHallId())) {
            throw new ResourceNotFoundException("Seat " + request.seatId() + " not found in study hall " + request.studyHallId());
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
        Program program = null;
        if (request.programId() != null) {
            program = programRepository.findById(request.programId()).filter(Program::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Program", request.programId()));
        }

        bookingRepository.expireStaleHolds(seat.getId(), now, BookingStatus.PENDING, BookingStatus.EXPIRED);
        if (bookingRepository.existsOverlapping(seat.getId(), start, end, BookingStatus.OCCUPYING)) {
            throw new ConflictException("SEAT_UNAVAILABLE",
                    "Seat " + seat.getSeatNumber() + " is already booked for some of the selected dates");
        }
        if (bookingRepository.existsUserOverlap(userId, hall.getId(), start, end, now)) {
            throw new ConflictException("DUPLICATE_BOOKING",
                    "You already have a booking in this study hall for some of these dates");
        }

        Booking booking = Booking.hold(newReference(), userId, seat, program, plan, start, end, unitPrice,
                now.plus(properties.holdDuration()));
        if (properties.autoConfirm()) {
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
        publish(booking, "BOOKING_CREATED", "Booked seat " + seat.getSeatNumber() + " at " + hall.getName()
                + " from " + start + " to " + end);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            enrollmentService.recordConfirmedBooking(booking);
            publish(booking, "BOOKING_CONFIRMED", "Booking " + booking.getBookingReference() + " confirmed");
        }
        return mapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse confirm(Long userId, Long bookingId) {
        Booking booking = ownBooking(userId, bookingId);
        booking.confirm(clock.instant());
        bookingRepository.saveAndFlush(booking);
        enrollmentService.recordConfirmedBooking(booking);
        publish(booking, "BOOKING_CONFIRMED", "Booking " + booking.getBookingReference() + " confirmed");
        return mapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancel(Long userId, Long bookingId, String reason) {
        Booking booking = ownBooking(userId, bookingId);
        booking.cancel(reason == null || reason.isBlank() ? null : reason.trim(), clock.instant(), today());
        bookingRepository.saveAndFlush(booking);
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
            publish(booking, "BOOKING_COMPLETED", "Booking " + booking.getBookingReference() + " completed");
        });
        return finished.size();
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
