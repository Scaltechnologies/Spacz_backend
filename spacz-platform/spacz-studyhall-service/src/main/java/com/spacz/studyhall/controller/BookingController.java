package com.spacz.studyhall.controller;

import com.spacz.studyhall.config.OpenApiConfig;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.dto.booking.CancelBookingRequest;
import com.spacz.studyhall.dto.booking.CreateBookingRequest;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.security.AuthenticatedUser;
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

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "A student's seat bookings (DAILY or MONTHLY)")
@SecurityRequirement(name = OpenApiConfig.BEARER)
@ApiErrorResponses
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Book a seat",
            description = "Creates a PENDING booking that holds the seat for 15 minutes; call /confirm after payment. "
                    + "The price is computed on the server; 409 SEAT_UNAVAILABLE when someone else got the seat.")
    public BookingResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                  @Valid @RequestBody CreateBookingRequest request) {
        return bookingService.create(user.userId(), request);
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

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Confirm a PENDING booking before its hold expires (payment success hook)")
    public BookingResponse confirm(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return bookingService.confirm(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Cancel a booking (PENDING, or CONFIRMED before its start date)")
    public BookingResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
                                  @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return bookingService.cancel(user.userId(), id, request == null ? null : request.reason());
    }
}
