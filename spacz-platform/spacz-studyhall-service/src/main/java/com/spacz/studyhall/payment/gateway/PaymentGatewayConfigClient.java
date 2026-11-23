package com.spacz.studyhall.payment.gateway;

import com.spacz.studyhall.config.ServiceClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;

/**
 * Resolves live Razorpay credentials for {@link RazorpayGateway}. The credentials themselves are
 * admin-configurable (admin-service PaymentGatewayConfigService) so an ADMIN can paste or rotate
 * test/live keys from the dashboard without a developer or a redeploy; this client just fetches and
 * caches what admin-service currently has configured.
 *
 * Caching keeps a fetch off the hot path of every payment call, and means a brief admin-service blip
 * doesn't take checkout down: a stale-but-still-valid cached credential is safer to keep using than to
 * fail hard on. While admin-service is unreachable the next attempt is deferred ~10s, so each payment
 * call doesn't wait for a connect timeout. RAZORPAY_* env vars are consulted only as a last resort.
 */
@Slf4j
@Component
public class PaymentGatewayConfigClient {

    private static final String PROVIDER = "RAZORPAY";
    private static final long RETRY_AFTER_FAILURE_SECONDS = 10;

    private final RestClient restClient;
    private final Clock clock;
    private final String fallbackKeyId;
    private final String fallbackKeySecret;
    private final String fallbackWebhookSecret;
    private final long cacheTtlSeconds;

    private GatewayCredentials cached;
    private Instant cachedAt = Instant.EPOCH;

    public PaymentGatewayConfigClient(ServiceClientFactory factory, Clock clock,
                                      @Value("${spacz.clients.admin-service-url}") String adminUrl,
                                      @Value("${razorpay.key-id:}") String fallbackKeyId,
                                      @Value("${razorpay.key-secret:}") String fallbackKeySecret,
                                      @Value("${razorpay.webhook-secret:}") String fallbackWebhookSecret,
                                      @Value("${spacz.payment.gateway-config.cache-ttl-seconds:60}") long cacheTtlSeconds) {
        this.restClient = factory.create(adminUrl);
        this.clock = clock;
        this.fallbackKeyId = fallbackKeyId;
        this.fallbackKeySecret = fallbackKeySecret;
        this.fallbackWebhookSecret = fallbackWebhookSecret;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    /** Returns null if no credentials are configured anywhere (neither admin-service nor env fallback). */
    public synchronized GatewayCredentials getCredentials() {
        Instant now = clock.instant();
        if (now.isBefore(cachedAt.plusSeconds(cacheTtlSeconds))) {
            return cached;
        }
        try {
            ActiveCredentialsDto dto = restClient.get()
                    .uri("/internal/payment-gateway/{provider}/active", PROVIDER)
                    .retrieve().body(ActiveCredentialsDto.class);
            if (dto != null && dto.keyId() != null && !dto.keyId().isBlank()) {
                return remember(new GatewayCredentials(dto.keyId(), dto.keySecret(), dto.webhookSecret()), now);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("No admin-configured {} credentials yet; using env fallback if present", PROVIDER);
        } catch (RestClientException e) {
            if (cached != null) {
                log.warn("Could not refresh {} credentials, keeping last cached value: {}", PROVIDER, e.getMessage());
                cachedAt = backOff(now);
                return cached;
            }
            log.warn("Could not fetch {} credentials from admin-service: {}", PROVIDER, e.getMessage());
            if (blank(fallbackKeyId)) {
                cachedAt = backOff(now);
                return null;
            }
        }
        if (!blank(fallbackKeyId)) {
            return remember(new GatewayCredentials(fallbackKeyId, fallbackKeySecret, fallbackWebhookSecret), now);
        }
        return remember(null, now);
    }

    /** Forget the cache (tests, or after an admin change when an immediate refresh is wanted). */
    public synchronized void invalidate() {
        cached = null;
        cachedAt = Instant.EPOCH;
    }

    private GatewayCredentials remember(GatewayCredentials credentials, Instant now) {
        cached = credentials;
        cachedAt = now;
        return credentials;
    }

    /** cachedAt such that the cache is considered fresh for another RETRY_AFTER_FAILURE_SECONDS. */
    private Instant backOff(Instant now) {
        return now.minusSeconds(cacheTtlSeconds).plusSeconds(RETRY_AFTER_FAILURE_SECONDS);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ActiveCredentialsDto(String provider, String mode, String keyId, String keySecret,
                                        String webhookSecret) {
        @Override
        public String toString() {
            return "ActiveCredentialsDto[provider=" + provider + ", keyId=" + keyId + "]";
        }
    }
}
