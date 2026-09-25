package com.spacz.admin.controller;

import com.spacz.admin.client.dto.BookingDto;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.service.BookingAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Bookings", description = "Platform-wide bookings overview")
public class AdminBookingController {

    private final BookingAdminService bookingService;

    @GetMapping
    @Operation(summary = "All bookings", description = "Filter by hall, user, status and overlapping date range")
    public PageResponse<BookingDto> bookings(@RequestParam(required = false) Long studyHallId,
                                             @RequestParam(required = false) Long userId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                             @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.bookings(studyHallId, userId, status, from, to, pageable);
    }
}
