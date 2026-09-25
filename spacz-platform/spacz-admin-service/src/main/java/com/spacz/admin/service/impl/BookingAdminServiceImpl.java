package com.spacz.admin.service.impl;

import com.spacz.admin.client.StudyHallServiceClient;
import com.spacz.admin.client.dto.BookingDto;
import com.spacz.admin.dto.PageResponse;
import com.spacz.admin.service.BookingAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BookingAdminServiceImpl implements BookingAdminService {

    private final StudyHallServiceClient studyHallClient;

    @Override
    public PageResponse<BookingDto> bookings(Long studyHallId, Long userId, String status, LocalDate from, LocalDate to,
                                             Pageable pageable) {
        return studyHallClient.searchBookings(studyHallId, userId, status, from, to, pageable);
    }
}
