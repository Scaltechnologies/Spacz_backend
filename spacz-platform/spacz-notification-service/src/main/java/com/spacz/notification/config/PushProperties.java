package com.spacz.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging credentials: the service-account JSON of the SPACZ Firebase project,
 * base64-encoded or as a file path. With neither, push is disabled and only logged.
 */
@ConfigurationProperties(prefix = "spacz.push")
public record PushProperties(Fcm fcm) {

    public record Fcm(String credentialsBase64, String credentialsFile) {

        public boolean configured() {
            return hasText(credentialsBase64) || hasText(credentialsFile);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    public Fcm fcmOrEmpty() {
        return fcm != null ? fcm : new Fcm(null, null);
    }
}
