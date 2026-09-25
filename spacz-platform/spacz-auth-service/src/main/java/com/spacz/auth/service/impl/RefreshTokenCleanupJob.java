package com.spacz.auth.service.impl;

import com.spacz.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /** Removes refresh tokens that expired more than a day ago. */
    @Scheduled(cron = "${spacz.auth.token-cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (deleted > 0) {
            log.info("Purged {} expired refresh tokens", deleted);
        }
    }
}
