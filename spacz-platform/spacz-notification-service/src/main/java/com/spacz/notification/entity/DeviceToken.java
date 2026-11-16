package com.spacz.notification.entity;

import com.spacz.notification.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * An FCM registration token of one installed app. It belongs to the account that registered it
 * last (another account logging in on the same device takes it over).
 */
@Entity
@Table(name = "device_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClientApp app;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DevicePlatform platform;

    @Column(nullable = false, length = 512, updatable = false)
    private String token;

    @Column(name = "app_version", length = 40)
    private String appVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public static DeviceToken register(String token, Instant now) {
        DeviceToken device = new DeviceToken();
        device.token = token;
        device.createdAt = now;
        return device;
    }

    /** (Re)assigns the token to the caller and refreshes what the app reported. */
    public void assign(Long userId, Role role, ClientApp app, DevicePlatform platform, String appVersion, Instant now) {
        this.userId = userId;
        this.role = role;
        this.app = app;
        this.platform = platform;
        this.appVersion = appVersion;
        this.lastSeenAt = now;
    }
}
