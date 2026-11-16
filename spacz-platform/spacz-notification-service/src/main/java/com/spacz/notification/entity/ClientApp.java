package com.spacz.notification.entity;

import com.spacz.notification.security.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The SPACZ app a device token belongs to. Each app is used by exactly one role.
 */
@Getter
@RequiredArgsConstructor
public enum ClientApp {
    /** Student app. */
    USER_APP(Role.USER),
    /** Partner / vendor app. */
    PARTNER_APP(Role.VENDOR),
    /** Admin panel (web push) or an admin mobile app. */
    ADMIN_APP(Role.ADMIN);

    private final Role role;
}
