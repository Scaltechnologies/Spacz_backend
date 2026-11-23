package com.spacz.studyhall.history;

import com.spacz.studyhall.entity.BookingStatus;

/**
 * Published (synchronously, inside the booking's transaction) every time a booking's status changes, and
 * when a booking is created ({@code fromStatus == null}). {@link BookingHistoryRecorder} turns it into a
 * {@link BookingStatusHistory} row that commits or rolls back together with the change itself.
 *
 * @param paymentId the payment that caused the change, if any
 */
public record BookingStatusChangedEvent(Long bookingId, BookingStatus fromStatus, BookingStatus toStatus,
                                        ActorType actorType, Long actorId, Long paymentId, String reason) {

    public static BookingStatusChangedEvent of(Long bookingId, BookingStatus from, BookingStatus to,
                                               ActorType actorType, Long actorId, String reason) {
        return new BookingStatusChangedEvent(bookingId, from, to, actorType, actorId, null, reason);
    }
}
