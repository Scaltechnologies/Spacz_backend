package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.BookingQuoteResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CancelBookingRequest;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.history.HistoryService;
import com.spacz.studyhall.history.HistoryViews.BookingHistory;
import com.spacz.studyhall.payment.dto.PaymentResponse;
import com.spacz.studyhall.payment.enums.PaymentErrorCode;
import com.spacz.studyhall.payment.enums.PaymentStatus;
import com.spacz.studyhall.payment.exception.PaymentException;
import com.spacz.studyhall.payment.service.PaymentService;
import com.spacz.studyhall.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import com.spacz.studyhall.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "A student's seat bookings (DAILY or MONTHLY)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final HistoryService historyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Book a seat",
            description = "Creates a PENDING booking that holds the seat for 15 minutes; pay for it via POST /api/payments "
                    + "(the booking is confirmed when the payment succeeds). "
                    + "The price is computed on the server; 409 SEAT_UNAVAILABLE when someone else got the seat.")
    public BookingResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(user.userId(), request);
    }

    @PostMapping("/quote")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Price a prospective booking",
            description = "Same body and validation as POST /api/bookings, without holding the seat. Returns base amount, "
                    + "the best applicable admin offer's discount, platform charges, taxes and total, all computed on the "
                    + "server. 409 SEAT_UNAVAILABLE / DUPLICATE_BOOKING and 422 errors as for booking.")
    public BookingQuoteResponse quote(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.quote(user.userId(), request);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "My bookings (history), newest first")
    public PageResponse<BookingResponse> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                              @RequestParam(required = false) BookingStatus status,
                                              @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.myBookings(user.userId(), status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One booking (its student, the hall's vendor, or ADMIN)")
    public BookingResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return bookingService.get(caller, id);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "A booking's full history (its student, the hall's vendor, or ADMIN)",
            description = "Every status change (who, when, why), every payment of the booking whatever its outcome, "
                    + "and the money ledger entries (payment received, refunds), oldest first.")
    public BookingHistory history(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return historyService.bookingHistory(caller, id);
    }

    /**
     * Students never confirm their own bookings: a booking becomes CONFIRMED only through a successful
     * payment (/api/payments). This endpoint is an ADMIN manual override (e.g. paid outside the app).
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "ADMIN only: confirm a PENDING booking without an in-app payment (manual override)",
            description = "Students pay via POST /api/payments; the booking is confirmed when the payment succeeds.")
    public BookingResponse confirm(@AuthenticationPrincipal AuthenticatedUser admin, @PathVariable Long id) {
        BookingResponse response = bookingService.confirmByAdmin(admin.userId(), id);
        log.info("Admin {} manually confirmed booking {}", admin.userId(), id);
        return response;
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel a booking (PENDING, or CONFIRMED before its start date)",
            description = "Cancelling a PENDING booking first cancels its open payment (the gateway is asked first). "
                    + "409 PAYMENT_ALREADY_SUCCESSFUL if that payment had in fact gone through: the booking is now "
                    + "CONFIRMED; cancel again to cancel the confirmed booking.")
    public BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request) {
        // Separate, committed step (payment lock first, like every payment path) before the booking changes.
        PaymentResponse payment = paymentService.cancelOpenPaymentForBooking(id, user.userId());
        if (payment != null && payment.status() == PaymentStatus.SUCCESS) {
            throw new PaymentException(PaymentErrorCode.PAYMENT_ALREADY_SUCCESSFUL,
                    "Your payment for this booking went through, so the booking is now confirmed. "
                            + "Cancel again if you still want to cancel it.");
        }
        return bookingService.cancel(user.userId(), id, request == null ? null : request.reason());
    }
}
