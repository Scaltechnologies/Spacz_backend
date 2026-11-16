package com.spacz.notification.dto;

import com.spacz.notification.entity.ClientApp;
import com.spacz.notification.entity.DevicePlatform;
import com.spacz.notification.entity.DeviceToken;

import java.time.Instant;

public record DeviceResponse(Long id, ClientApp app, DevicePlatform platform, String appVersion, Instant createdAt,
                             Instant lastSeenAt) {

    public static DeviceResponse from(DeviceToken device) {
        return new DeviceResponse(device.getId(), device.getApp(), device.getPlatform(), device.getAppVersion(),
                device.getCreatedAt(), device.getLastSeenAt());
    }
}
