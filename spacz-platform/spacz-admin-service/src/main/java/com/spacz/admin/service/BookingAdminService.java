package com.spacz.admin.service;

import com.spacz.admin.client.dto.BookingDto;
import com.spacz.admin.dto.PageResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Read-only bookings overview (owned by studyhall-service).
 */
public interface BookingAdminService {

    PageResponse<BookingDto> bookings(Long studyHallId, Long userId, String status, LocalDate from, LocalDate to,
                                      Pageable pageable);
}
