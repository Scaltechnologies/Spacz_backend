package com.spacz.notification.dto;

import com.spacz.notification.entity.ClientApp;
import com.spacz.notification.entity.DevicePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterDeviceRequest(
        @Schema(description = "FCM registration token from the Firebase SDK on the device")
        @NotBlank @Size(max = 512) String token,
        @Schema(description = "USER_APP = student app, PARTNER_APP = partner/vendor app, ADMIN_APP = admin panel")
        @NotNull ClientApp app,
        @NotNull DevicePlatform platform,
        @Size(max = 40) String appVersion) {
}
