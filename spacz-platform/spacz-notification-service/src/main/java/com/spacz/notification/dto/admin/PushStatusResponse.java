package com.spacz.notification.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record PushStatusResponse(
        @Schema(description = "FCM when Firebase credentials are configured, DISABLED otherwise (in-app only)")
        String provider,
        @Schema(description = "Registered devices per role")
        Map<String, Long> devices) {
}
