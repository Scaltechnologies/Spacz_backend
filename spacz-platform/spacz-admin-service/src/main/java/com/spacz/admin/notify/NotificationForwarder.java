package com.spacz.admin.notify;

import com.spacz.admin.config.ServiceClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Best effort: a notification-service outage never fails the business operation. Transient errors
 * are retried a few times (the dedupKey makes that safe); after that the notification is logged and dropped.
 */
@Slf4j
@Component
public class NotificationForwarder {

    static final int ATTEMPTS = 3;

    private final RestClient restClient;
    private final boolean enabled;
    private final long retryDelayMillis;

    public NotificationForwarder(ServiceClientFactory factory,
                                 @Value("${spacz.clients.notification-service-url}") String baseUrl,
                                 @Value("${spacz.notifications.enabled:true}") boolean enabled,
                                 @Value("${spacz.notifications.retry-delay-millis:2000}") long retryDelayMillis) {
        this.restClient = factory.create(baseUrl);
        this.enabled = enabled;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Async("notificationForwardExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void forward(NotificationEvent event) {
        if (!enabled) {
            return;
        }
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                restClient.post().uri("/internal/notifications").body(event).retrieve().toBodilessEntity();
                return;
            } catch (HttpClientErrorException ex) {
                log.warn("Notification {} rejected by notification-service: {} {}", event.type(), ex.getStatusCode(),
                        ex.getResponseBodyAsString());
                return;
            } catch (RestClientException ex) {
                if (attempt == ATTEMPTS) {
                    log.warn("Notification {} for {} not delivered after {} attempts: {}", event.type(),
                            event.recipientUserId() != null ? "user " + event.recipientUserId() : event.audience(),
                            ATTEMPTS, ex.getMessage());
                    return;
                }
                try {
                    Thread.sleep(retryDelayMillis * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
