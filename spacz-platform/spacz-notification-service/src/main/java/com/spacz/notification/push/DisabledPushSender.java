package com.spacz.notification.push;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Used when Firebase is not configured: nothing is sent, in-app notifications still work.
 */
@Slf4j
public class DisabledPushSender implements PushSender {

    @Override
    public String provider() {
        return "DISABLED";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public PushResult send(PushMessage message, List<String> tokens) {
        log.debug("Push disabled; not sending '{}' to {} device(s)", message.title(), tokens.size());
        return PushResult.NONE;
    }
}
