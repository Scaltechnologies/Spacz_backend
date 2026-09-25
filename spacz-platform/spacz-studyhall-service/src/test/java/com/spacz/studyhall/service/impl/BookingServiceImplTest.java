package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingPlan;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.mapper.BookingMapper;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.support.VendorAccess;
import com.spacz.studyhall.support.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-25T04:30:00Z");       // 10:00 IST
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final long USER_ID = 900L;

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private ProgramRepository programRepository;
    @Mock
    private VendorAccess vendorAccess;
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private ApplicationEventPublisher events;

    private StudyHall hall;
    private Block block;
    private Seat seat;

    @BeforeEach
    void setUp() {
        hall = Fixtures.activeHall(10L, Fixtures.approvedVendor(1L), "150.00", null);
        block = Fixtures.block(5L, hall);
        seat = Fixtures.seat(7L, block, "A1");
    }

    private BookingServiceImpl service(boolean autoConfirm) {
        BookingProperties properties = new BookingProperties(Duration.ofMinutes(15), autoConfirm, 90, 90,
                ZoneId.of("Asia/Kolkata"));
        return new BookingServiceImpl(bookingRepository, seatRepository, programRepository, vendorAccess,
                enrollmentService, userServiceClient, new BookingMapper(), properties, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void seatIsFree() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));
        when(bookingRepository.existsOverlapping(eq(7L), any(), any(), eq(BookingStatus.OCCUPYING))).thenReturn(false);
        when(bookingRepository.existsUserOverlap(eq(USER_ID), eq(10L), any(), any(), eq(NOW))).thenReturn(false);
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            ReflectionTestUtils.setField(booking, "id", 55L);
            return booking;
        });
    }

    private static CreateBookingRequest daily(long hallId, LocalDate start, LocalDate end) {
        return new CreateBookingRequest(hallId, 7L, BookingPlan.DAILY, start, end, null, null);
    }

    @Test
    void createsAPendingHoldPricedByTheServer() {
        seatIsFree();

        BookingResponse response = service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(3)));

        assertThat(response.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(response.units()).isEqualTo(3);
        assertThat(response.totalPrice()).isEqualByComparingTo("450.00");
        assertThat(response.holdExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(response.bookingReference()).startsWith("SPZ-");
        verify(bookingRepository).expireStaleHolds(7L, NOW, BookingStatus.PENDING, BookingStatus.EXPIRED);
        verify(enrollmentService, never()).recordConfirmedBooking(any());
    }

    @Test
    void monthlyPlanUsesTheBlockMonthlyPriceAndConfirmationCreatesAnEnrollment() {
        block.setMonthlyPrice(new BigDecimal("2500.00"));
        seatIsFree();

        BookingResponse response = service(true).create(USER_ID,
                new CreateBookingRequest(10L, 7L, BookingPlan.MONTHLY, TODAY.plusDays(1), null, 2, null));

        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.units()).isEqualTo(2);
        assertThat(response.endDate()).isEqualTo(TODAY.plusDays(1).plusMonths(2).minusDays(1));
        assertThat(response.totalPrice()).isEqualByComparingTo("5000.00");
        verify(enrollmentService).recordConfirmedBooking(any(Booking.class));
    }

    @Test
    void monthlyPlanWithoutAMonthlyPriceIsRefused() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service(false).create(USER_ID,
                new CreateBookingRequest(10L, 7L, BookingPlan.MONTHLY, TODAY.plusDays(1), null, 1, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("PLAN_NOT_OFFERED");
    }

    @Test
    void overlappingBookingIsRejectedWith409() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));
        when(bookingRepository.existsOverlapping(eq(7L), any(), any(), eq(BookingStatus.OCCUPYING))).thenReturn(true);

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SEAT_UNAVAILABLE");
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void databaseExclusionConstraintViolationIsReportedAsSeatUnavailable() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));
        when(bookingRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.existsUserOverlap(anyLong(), anyLong(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ex_bookings_seat_no_overlap"));

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SEAT_UNAVAILABLE");
    }

    @Test
    void userCannotHoldTwoSeatsInTheSameHallOnTheSameDates() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));
        when(bookingRepository.existsOverlapping(anyLong(), any(), any(), any())).thenReturn(false);
        when(bookingRepository.existsUserOverlap(eq(USER_ID), eq(10L), any(), any(), eq(NOW))).thenReturn(true);

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(2))))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("DUPLICATE_BOOKING");
    }

    @Test
    void pastStartDateIsRejected() {
        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.minusDays(1), TODAY.plusDays(1))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("INVALID_DATE_RANGE");
    }

    @Test
    void tooLongDailyBookingIsRejected() {
        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY, TODAY.plusDays(90))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void seatMustBelongToTheRequestedHall() {
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(999L, TODAY.plusDays(1), TODAY.plusDays(1))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reservedSeatCannotBeBookedOnline() {
        seat.setStatus(SeatStatus.RESERVED);
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(1))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("SEAT_NOT_BOOKABLE");
    }

    @Test
    void hallOfASuspendedVendorCannotBeBooked() {
        hall.getVendor().apply(StatusAction.SUSPEND, "KYC expired", NOW);
        when(seatRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(seat));

        assertThatThrownBy(() -> service(false).create(USER_ID, daily(10L, TODAY.plusDays(1), TODAY.plusDays(1))))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("STUDY_HALL_NOT_BOOKABLE");
    }

    @Test
    void expiredHoldCannotBeConfirmed() {
        Booking booking = Booking.hold("SPZ-TEST", USER_ID, seat, null, BookingPlan.DAILY, TODAY.plusDays(1),
                TODAY.plusDays(1), new BigDecimal("150.00"), NOW.minusSeconds(1));
        when(bookingRepository.findDetailed(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service(false).confirm(USER_ID, 1L))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("BOOKING_HOLD_EXPIRED");
    }

    @Test
    void someoneElsesBookingIsNotFound() {
        Booking booking = Booking.hold("SPZ-TEST", 12345L, seat, null, BookingPlan.DAILY, TODAY.plusDays(1),
                TODAY.plusDays(1), new BigDecimal("150.00"), NOW.plusSeconds(600));
        when(bookingRepository.findDetailed(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service(false).cancel(USER_ID, 1L, "changed plans"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmedBookingCannotBeCancelledOnceStarted() {
        Booking booking = Booking.hold("SPZ-TEST", USER_ID, seat, null, BookingPlan.DAILY, TODAY, TODAY.plusDays(2),
                new BigDecimal("150.00"), NOW.plusSeconds(600));
        booking.confirm(NOW);
        when(bookingRepository.findDetailed(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> service(false).cancel(USER_ID, 1L, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("BOOKING_ALREADY_STARTED");
    }
}
