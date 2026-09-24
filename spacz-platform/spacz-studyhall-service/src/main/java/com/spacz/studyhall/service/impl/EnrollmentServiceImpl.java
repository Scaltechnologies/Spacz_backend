package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.client.dto.UserSummaryDto;
import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.enrollment.CreateEnrollmentRequest;
import com.spacz.studyhall.dto.enrollment.EnrollmentResponse;
import com.spacz.studyhall.dto.enrollment.StudentInfo;
import com.spacz.studyhall.dto.enrollment.UpdateEnrollmentRequest;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.Enrollment;
import com.spacz.studyhall.entity.EnrollmentSource;
import com.spacz.studyhall.entity.EnrollmentStatus;
import com.spacz.studyhall.entity.Program;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.DuplicateResourceException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.mapper.EnrollmentMapper;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.EnrollmentRepository;
import com.spacz.studyhall.repository.EnrollmentSpecifications;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.support.PageableSupport;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private static final Set<String> SORTABLE = Set.of("startDate", "endDate", "createdAt", "status");

    private final EnrollmentRepository enrollmentRepository;
    private final SeatRepository seatRepository;
    private final ProgramRepository programRepository;
    private final BookingRepository bookingRepository;
    private final VendorAccess vendorAccess;
    private final UserServiceClient userServiceClient;
    private final EnrollmentMapper mapper;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EnrollmentResponse> students(Long vendorId, Long studyHallId, EnrollmentStatus status,
                                                     Long programId, String search, Pageable pageable) {
        if (studyHallId != null) {
            vendorAccess.ownedHall(vendorId, studyHallId);
        } else {
            vendorAccess.vendor(vendorId);
        }
        Specification<Enrollment> spec = Specification.where(EnrollmentSpecifications.fetchDetails())
                .and(EnrollmentSpecifications.forVendor(vendorId))
                .and(EnrollmentSpecifications.forHall(studyHallId))
                .and(EnrollmentSpecifications.withStatus(status))
                .and(EnrollmentSpecifications.forProgram(programId))
                .and(EnrollmentSpecifications.guestMatches(search));
        Page<Enrollment> page = enrollmentRepository.findAll(spec,
                PageableSupport.restrictSort(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "startDate")));
        Map<Long, UserSummaryDto> accounts = lookup(page.getContent().stream().map(Enrollment::getUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        return PageResponse.from(page, e -> mapper.toResponse(e, student(e, accounts)));
    }

    @Override
    @Transactional
    public EnrollmentResponse create(Long vendorId, Long studyHallId, CreateEnrollmentRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        Map<Long, UserSummaryDto> accounts = Map.of();
        if (request.userId() != null) {
            accounts = userServiceClient.findUsers(Set.of(request.userId())).stream()
                    .collect(Collectors.toMap(UserSummaryDto::userId, Function.identity(), (a, b) -> a));
            if (!accounts.containsKey(request.userId())) {
                throw new ResourceNotFoundException("Student", request.userId());
            }
            if (enrollmentRepository.findByStudyHallIdAndUserIdAndStatus(studyHallId, request.userId(),
                    EnrollmentStatus.ACTIVE).isPresent()) {
                throw new DuplicateResourceException("This student already has an active enrollment in this study hall");
            }
        }
        Enrollment enrollment = Enrollment.start(hall, request.userId(), EnrollmentSource.WALK_IN,
                request.plan() != null ? request.plan() : BookingPlan.MONTHLY, request.startDate(), request.endDate());
        if (request.userId() == null) {
            enrollment.setGuestName(request.guestName().trim());
            enrollment.setGuestPhone(trimToNull(request.guestPhone()));
            enrollment.setGuestEmail(trimToNull(request.guestEmail()));
        }
        enrollment.setProgram(program(request.programId()));
        enrollment.setNotes(trimToNull(request.notes()));
        if (request.seatId() != null) {
            enrollment.setSeat(holdSeat(studyHallId, request.seatId(), request.startDate()));
        }
        Enrollment saved = enrollmentRepository.saveAndFlush(enrollment);
        return mapper.toResponse(saved, student(saved, accounts));
    }

    @Override
    @Transactional
    public EnrollmentResponse update(Long vendorId, Long studyHallId, Long enrollmentId, UpdateEnrollmentRequest request) {
        vendorAccess.writableHall(vendorId, studyHallId);
        Enrollment enrollment = enrollmentRepository.findByIdAndStudyHallId(enrollmentId, studyHallId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment", enrollmentId));
        if (request.programId() != null) {
            enrollment.setProgram(program(request.programId()));
        }
        if (request.notes() != null) {
            enrollment.setNotes(trimToNull(request.notes()));
        }
        if (request.endDate() != null) {
            if (request.endDate().isBefore(enrollment.getStartDate())) {
                throw new BusinessRuleException("INVALID_DATE_RANGE", "endDate must not be before startDate");
            }
            enrollment.setEndDate(request.endDate());
        }
        if (request.seatId() != null && (enrollment.getSeat() == null || !enrollment.getSeat().getId().equals(request.seatId()))) {
            if (enrollment.getSource() != EnrollmentSource.WALK_IN) {
                throw new BusinessRuleException("The seat of an online booking is changed through the booking, not here");
            }
            releaseSeat(enrollment);
            enrollment.setSeat(holdSeat(studyHallId, request.seatId(), today()));
        }
        if (request.status() != null && request.status() != enrollment.getStatus()) {
            enrollment.finish(request.status());
            releaseSeat(enrollment);
        }
        Enrollment saved = enrollmentRepository.saveAndFlush(enrollment);
        Map<Long, UserSummaryDto> accounts = saved.getUserId() == null ? Map.of() : lookup(Set.of(saved.getUserId()));
        return mapper.toResponse(saved, student(saved, accounts));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentResponse> forUser(Long userId) {
        return enrollmentRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(e -> mapper.toResponse(e, null))
                .toList();
    }

    @Override
    @Transactional
    public void recordConfirmedBooking(Booking booking) {
        enrollmentRepository.findByStudyHallIdAndUserIdAndStatus(booking.getStudyHall().getId(), booking.getUserId(),
                        EnrollmentStatus.ACTIVE)
                .ifPresentOrElse(existing -> {
                    existing.extendTo(booking.getEndDate());
                    if (existing.getSource() == EnrollmentSource.BOOKING) {
                        existing.setSeat(booking.getSeat());
                    }
                    if (booking.getProgram() != null) {
                        existing.setProgram(booking.getProgram());
                    }
                }, () -> {
                    Enrollment enrollment = Enrollment.start(booking.getStudyHall(), booking.getUserId(),
                            EnrollmentSource.BOOKING, booking.getPlan(), booking.getStartDate(), booking.getEndDate());
                    enrollment.setSeat(booking.getSeat());
                    enrollment.setProgram(booking.getProgram());
                    enrollmentRepository.save(enrollment);
                });
    }

    @Override
    @Transactional
    public int completeEnded(int batchSize) {
        List<Enrollment> ended = enrollmentRepository.findEnded(today(), PageRequest.of(0, batchSize));
        ended.forEach(enrollment -> {
            enrollment.finish(EnrollmentStatus.COMPLETED);
            releaseSeat(enrollment);
        });
        return ended.size();
    }

    /** Walk-in students hold their seat: it becomes RESERVED (not bookable online). */
    private Seat holdSeat(Long studyHallId, Long seatId, LocalDate from) {
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .filter(s -> s.getStudyHall().getId().equals(studyHallId))
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));
        if (seat.getStatus() != SeatStatus.AVAILABLE) {
            throw new ConflictException("SEAT_UNAVAILABLE", "Seat " + seat.getSeatNumber() + " is " + seat.getStatus());
        }
        if (bookingRepository.existsUpcomingOnSeat(seatId, from, clock.instant())) {
            throw new ConflictException("SEAT_UNAVAILABLE",
                    "Seat " + seat.getSeatNumber() + " has upcoming online bookings; choose another seat");
        }
        seat.setStatus(SeatStatus.RESERVED);
        return seat;
    }

    private static void releaseSeat(Enrollment enrollment) {
        if (enrollment.holdsSeat() && enrollment.getSeat().getStatus() == SeatStatus.RESERVED) {
            enrollment.getSeat().setStatus(SeatStatus.AVAILABLE);
        }
    }

    private Program program(Long programId) {
        if (programId == null) {
            return null;
        }
        return programRepository.findById(programId).filter(Program::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Program", programId));
    }

    private Map<Long, UserSummaryDto> lookup(Set<Long> userIds) {
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

    private static StudentInfo student(Enrollment e, Map<Long, UserSummaryDto> accounts) {
        if (e.getUserId() == null) {
            return EnrollmentMapper.guest(e);
        }
        UserSummaryDto s = accounts.get(e.getUserId());
        return s == null
                ? new StudentInfo(e.getUserId(), null, null, null, true)
                : new StudentInfo(e.getUserId(), BookingServiceImpl.fullName(s), s.phone(), s.email(), true);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(bookingProperties.zone()));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
