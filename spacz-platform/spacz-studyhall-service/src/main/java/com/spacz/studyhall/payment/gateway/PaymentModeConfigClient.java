package com.spacz.studyhall.payment.gateway;

import com.spacz.studyhall.config.ServiceClientFactory;
import com.spacz.studyhall.payment.enums.PaymentMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Resolves the platform-wide payment mode (ONLINE_ONLY / PAY_AT_HALL_ONLY / BOTH), an admin setting
 * (PAYMENT_MODE in admin-service) that lets an ADMIN take online checkout out of rotation while
 * Razorpay's live keys aren't provisioned, without a developer or a redeploy.
 *
 * Same poll-and-cache shape as {@link PaymentGatewayConfigClient}: no fetch on the hot path of every
 * payment call, and a brief admin-service blip doesn't take checkout down. Defaults to BOTH (the
 * unrestricted behavior) until admin-service has ever been reachable, since that's the safest thing to
 * fail open to. While admin-service is down the next attempt is deferred ~10s.
 */
@Slf4j
@Component
public class PaymentModeConfigClient {

    private static final String SETTING_KEY = "PAYMENT_MODE";
    private static final long RETRY_AFTER_FAILURE_SECONDS = 10;
    private static final ParameterizedTypeReference<Map<String, String>> MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final Clock clock;
    private final long cacheTtlSeconds;

    private PaymentMode cached = PaymentMode.BOTH;
    private Instant cachedAt = Instant.EPOCH;

    public PaymentModeConfigClient(ServiceClientFactory factory, Clock clock,
                                   @Value("${spacz.clients.admin-service-url}") String adminUrl,
                                   @Value("${spacz.payment.mode-config.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.restClient = factory.create(adminUrl);
        this.clock = clock;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    public synchronized PaymentMode getCurrentMode() {
        Instant now = clock.instant();
        if (now.isBefore(cachedAt.plusSeconds(cacheTtlSeconds))) {
            return cached;
        }
        try {
            Map<String, String> dto = restClient.get().uri("/internal/settings/{key}", SETTING_KEY)
                    .retrieve().body(MAP);
            String value = dto != null ? dto.get("value") : null;
            if (value != null && !value.isBlank()) {
                cached = PaymentMode.valueOf(value.trim());
            } else {
                log.debug("No {} setting configured yet; continuing with {}", SETTING_KEY, cached);
            }
            cachedAt = now;
        } catch (IllegalArgumentException e) {
            log.warn("admin-service returned an unrecognized {} value; continuing with {}: {}",
                    SETTING_KEY, cached, e.getMessage());
            cachedAt = backOff(now);
        } catch (RestClientException e) {
            log.warn("Could not refresh {} from admin-service, continuing with the last known value {}: {}",
                    SETTING_KEY, cached, e.getMessage());
            cachedAt = backOff(now);
        }
        return cached;
    }

    private Instant backOff(Instant now) {
        return now.minusSeconds(cacheTtlSeconds).plusSeconds(RETRY_AFTER_FAILURE_SECONDS);
    }
}
