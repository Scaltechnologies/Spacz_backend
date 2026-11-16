package com.spacz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Phone Auth. The app sends the SMS and checks the code with the Firebase SDK, then hands
 * the resulting Firebase ID token to {@code POST /api/auth/otp/firebase}.
 *
 * @param projectId Firebase project ID (Project settings → General). Blank disables the route.
 */
@ConfigurationProperties(prefix = "spacz.firebase")
public record FirebaseProperties(String projectId) {

    public boolean enabled() {
        return projectId != null && !projectId.isBlank();
    }
}
