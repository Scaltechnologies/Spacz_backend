package com.spacz.user.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Sends notifications through notification-service. Inside a transaction the notification is
 * delivered only after commit, so rolled-back work never notifies anyone.
 */
@Component
@RequiredArgsConstructor
public class Notifications {

    private final ApplicationEventPublisher publisher;

    public void send(NotificationEvent event) {
        publisher.publishEvent(event);
    }
}
