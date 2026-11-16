package com.spacz.notification.service;

import com.spacz.notification.config.NotificationProperties;
import com.spacz.notification.dto.DeviceResponse;
import com.spacz.notification.dto.RegisterDeviceRequest;
import com.spacz.notification.entity.DeviceToken;
import com.spacz.notification.exception.BusinessRuleException;
import com.spacz.notification.repository.DeviceTokenRepository;
import com.spacz.notification.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationProperties properties;
    private final Clock clock;

    /**
     * Registers or refreshes the caller's device. Apps call this after login and whenever Firebase
     * hands them a new token; a token registered by another account moves to the caller.
     */
    @Transactional
    public DeviceResponse register(AuthenticatedUser caller, RegisterDeviceRequest request) {
        if (request.app().getRole() != caller.role()) {
            throw new BusinessRuleException("APP_ROLE_MISMATCH",
                    request.app() + " is not the app for " + caller.role() + " accounts");
        }
        Instant now = clock.instant();
        String token = request.token().trim();
        DeviceToken device = deviceTokenRepository.findByToken(token).orElseGet(() -> DeviceToken.register(token, now));
        if (device.getUserId() != null && !device.getUserId().equals(caller.userId())) {
            log.info("Device token moves from account {} to account {}", device.getUserId(), caller.userId());
        }
        device.assign(caller.userId(), caller.role(), request.app(), request.platform(),
                request.appVersion() == null || request.appVersion().isBlank() ? null : request.appVersion().trim(), now);
        device = deviceTokenRepository.saveAndFlush(device);
        dropOldestBeyondLimit(caller.userId());
        return DeviceResponse.from(device);
    }

    /** Called on logout. Unknown tokens (or another account's) are ignored. */
    @Transactional
    public void unregister(AuthenticatedUser caller, String token) {
        deviceTokenRepository.deleteByTokenAndUserId(token.trim(), caller.userId());
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> mine(AuthenticatedUser caller) {
        return deviceTokenRepository.findByUserIdOrderByLastSeenAtDesc(caller.userId()).stream()
                .map(DeviceResponse::from).toList();
    }

    private void dropOldestBeyondLimit(Long userId) {
        List<DeviceToken> devices = deviceTokenRepository.findByUserIdOrderByLastSeenAtDesc(userId);
        if (devices.size() > properties.maxDevicesPerUser()) {
            deviceTokenRepository.deleteAll(devices.subList(properties.maxDevicesPerUser(), devices.size()));
        }
    }
}
