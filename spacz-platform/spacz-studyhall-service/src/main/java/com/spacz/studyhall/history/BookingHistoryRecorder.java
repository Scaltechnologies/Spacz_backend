package com.spacz.studyhall.history;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Writes {@link BookingStatusHistory}. A plain (not transactional-phase) listener on purpose: it runs inside
 * the publisher's transaction, so the history row and the status change it describes commit together.
 */
@Component
@RequiredArgsConstructor
public class BookingHistoryRecorder {

    private final BookingStatusHistoryRepository repository;
    private final Clock clock;

    @EventListener
    public void onStatusChanged(BookingStatusChangedEvent event) {
        if (event.fromStatus() == event.toStatus()) {
            return;
        }
        repository.save(BookingStatusHistory.builder()
                .bookingId(event.bookingId())
                .fromStatus(event.fromStatus())
                .toStatus(event.toStatus())
                .actorType(event.actorType())
                .actorId(event.actorId())
                .paymentId(event.paymentId())
                .reason(truncate(event.reason()))
                .createdAt(clock.instant())
                .build());
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
