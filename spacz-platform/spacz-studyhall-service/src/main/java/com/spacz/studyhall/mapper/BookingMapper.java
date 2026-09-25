package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.booking.BookingResponse;
import com.spacz.studyhall.entity.Booking;
import org.springframework.stereotype.Component;

@Component
public class BookingMapper {

    public BookingResponse toResponse(Booking b) {
        return new BookingResponse(b.getId(), b.getBookingReference(), b.getUserId(), b.getStudyHall().getId(),
                b.getStudyHall().getName(), b.getStudyHall().getCity(), b.getSeat().getId(),
                b.getSeat().getSeatNumber(), b.getProgram() == null ? null : b.getProgram().getId(),
                b.getProgram() == null ? null : b.getProgram().getName(), b.getPlan(), b.getStartDate(),
                b.getEndDate(), b.units(), b.getUnitPrice(), b.getTotalPrice(), b.getStatus(), b.getHoldExpiresAt(),
                b.getConfirmedAt(), b.getCancelledAt(), b.getCancellationReason(), b.getCreatedAt(), b.getUpdatedAt());
    }
}
