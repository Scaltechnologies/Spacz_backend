package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.booking.VendorBookingResponse;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BookingService {

    BookingResponse create(Long userId, CreateBookingRequest request);

    BookingResponse confirm(Long userId, Long bookingId);

    BookingResponse cancel(Long userId, Long bookingId, String reason);

    PageResponse<BookingResponse> myBookings(Long userId, BookingStatus status, Pageable pageable);

    /** Visible to the booking's user, the hall's vendor and admins; 404 for everyone else. */
    BookingResponse get(AuthenticatedUser caller, Long bookingId);

    PageResponse<VendorBookingResponse> vendorBookings(Long vendorId, Long studyHallId, BookingStatus status,
                                                      LocalDate from, LocalDate to, Pageable pageable);

    PageResponse<BookingResponse> search(Long studyHallId, Long userId, BookingStatus status, LocalDate from,
                                         LocalDate to, Pageable pageable);

    /** PENDING bookings past their hold → EXPIRED. Returns the number processed. */
    int expireHolds(int batchSize);

    /** CONFIRMED bookings that ended before today → COMPLETED. Returns the number processed. */
    int completeFinished(int batchSize);
}
