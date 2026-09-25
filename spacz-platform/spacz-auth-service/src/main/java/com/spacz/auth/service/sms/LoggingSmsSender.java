package com.spacz.auth.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Development sender: writes the message (including the code) to the log instead of sending an SMS.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "spacz.otp", name = "sms-provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phone, String message) {
        log.info("[DEV SMS] to {}: {}", phone, message);
    }
}
