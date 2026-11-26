package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.BookingQuoteResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.dto.booking.VendorBookingResponse;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface BookingService {

    BookingResponse create(Long userId, CreateBookingRequest request);

    /** Validates a prospective booking and returns its server-computed price without holding the seat. */
    BookingQuoteResponse quote(Long userId, CreateBookingRequest request);

    /**
     * ADMIN manual/offline override: confirms a PENDING booking without a payment (e.g. paid outside the
     * app). Students never confirm their own bookings; that happens only through a successful payment.
     */
    default BookingResponse confirmByAdmin(Long adminId, Long bookingId) {
        return confirmByAdmin(adminId, bookingId, null);
    }

    /** ADMIN manual override; {@code reason} (optional) is stored in the booking's status history. */
    BookingResponse confirmByAdmin(Long adminId, Long bookingId, String reason);

    /** Outcome of {@link #confirmAfterPayment}. */
    enum PaidConfirmation {
        CONFIRMED,
        /** Already CONFIRMED / COMPLETED (e.g. a second payment, or an earlier admin confirmation). */
        ALREADY_CONFIRMED,
        /** Cancelled, or expired and the seat (or the student's slot in the hall) was taken meanwhile. */
        SEAT_LOST
    }

    /**
     * Called only by the payment finalize step, inside its transaction (which already holds the payment
     * row lock): confirms a PENDING booking, or revives an EXPIRED one if the seat is still free.
     * {@code actorType}/{@code actorId} (the confirming vendor for Pay at Hall, else SYSTEM) go into the
     * booking's status history.
     */
    PaidConfirmation confirmAfterPayment(Long bookingId, Long paymentId,
                                         com.spacz.studyhall.history.ActorType actorType, Long actorId);

    /** Keeps a PENDING booking's seat held until {@code until} while a payment session is open. */
    void extendHoldForPayment(Long bookingId, java.time.Instant until);

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
