package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.StatsResponse;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.EnrollmentStatus;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.EnrollmentRepository;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.ProgramSpecifications;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final VendorProfileRepository vendorRepository;
    private final StudyHallRepository studyHallRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ProgramRepository programRepository;
    private final BookingProperties bookingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public StatsResponse stats() {
        LocalDate today = LocalDate.now(clock.withZone(bookingProperties.zone()));
        Instant dayStart = today.atStartOfDay(bookingProperties.zone()).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(bookingProperties.zone()).toInstant();
        return new StatsResponse(
                vendorRepository.count(),
                vendorRepository.countByStatus(VendorStatus.DRAFT),
                vendorRepository.countByStatus(VendorStatus.PENDING),
                vendorRepository.countByStatus(VendorStatus.APPROVED),
                vendorRepository.countByStatus(VendorStatus.ACTIVE),
                vendorRepository.countByStatus(VendorStatus.SUSPENDED),
                vendorRepository.countByStatus(VendorStatus.REJECTED),
                studyHallRepository.count(),
                studyHallRepository.countByStatus(StudyHallStatus.ACTIVE),
                studyHallRepository.countByStatus(StudyHallStatus.PENDING_APPROVAL),
                studyHallRepository.countByStatus(StudyHallStatus.SUSPENDED),
                seatRepository.count(),
                bookingRepository.count(),
                bookingRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(dayStart, dayEnd),
                bookingRepository.countConfirmedOn(today),
                bookingRepository.countByStatus(BookingStatus.PENDING),
                bookingRepository.countByStatus(BookingStatus.CONFIRMED),
                enrollmentRepository.countByStatus(EnrollmentStatus.ACTIVE),
                programRepository.count(ProgramSpecifications.matching(null, null, true)));
    }
}
