package com.spacz.auth.service.sms;

/**
 * Sends a text message. Implement this for the SMS gateway in use (MSG91, Twilio, AWS SNS ...) and
 * select it with {@code spacz.otp.sms-provider}.
 */
public interface SmsSender {

    void send(String phone, String message);
}
