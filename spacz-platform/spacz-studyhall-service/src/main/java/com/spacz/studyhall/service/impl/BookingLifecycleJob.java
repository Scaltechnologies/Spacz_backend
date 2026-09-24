package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.service.BookingService;
import com.spacz.studyhall.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Time-based transitions of bookings and enrollments. Safe on several instances: a row changed by
 * another instance fails the optimistic-lock check and is picked up on the next run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingLifecycleJob {

    private static final int BATCH_SIZE = 200;

    private final BookingService bookingService;
    private final EnrollmentService enrollmentService;

    @Scheduled(fixedDelayString = "${spacz.booking.lifecycle-interval-ms:60000}", initialDelay = 30000)
    public void run() {
        try {
            int expired = bookingService.expireHolds(BATCH_SIZE);
            int completed = bookingService.completeFinished(BATCH_SIZE);
            int ended = enrollmentService.completeEnded(BATCH_SIZE);
            if (expired + completed + ended > 0) {
                log.info("Lifecycle: {} holds expired, {} bookings completed, {} enrollments ended", expired, completed, ended);
            }
        } catch (OptimisticLockingFailureException ex) {
            log.debug("Lifecycle run skipped rows changed concurrently: {}", ex.getMessage());
        }
    }
}
