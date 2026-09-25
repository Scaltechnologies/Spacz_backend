package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.client.UserServiceClient;
import com.spacz.studyhall.client.dto.RecordActivityRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Best effort: a user-service outage never affects bookings, it only leaves a gap in activity history.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingActivityListener {

    private final UserServiceClient userServiceClient;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingActivity(BookingActivityEvent event) {
        try {
            userServiceClient.recordActivity(event.userId(), new RecordActivityRequest(event.activityType(),
                    event.description(), "BOOKING", event.bookingId()));
        } catch (RuntimeException ex) {
            log.warn("Could not record {} activity for booking {}: {}", event.activityType(), event.bookingId(),
                    ex.getMessage());
        }
    }
}
