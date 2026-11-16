package com.spacz.notification.push;

import java.util.List;

public interface PushSender {

    /** FCM, or DISABLED when no credentials are configured. */
    String provider();

    boolean enabled();

    /** Sends one message to the given device tokens. Never throws for delivery failures. */
    PushResult send(PushMessage message, List<String> tokens);
}
