package com.spacz.auth.entity;

import com.spacz.auth.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;

/**
 * An identity on the platform: login identifiers (email and/or phone), credentials and status only.
 * Profiles live in user-service (students) and studyhall-service (vendors). Phone-OTP accounts
 * have no password until the user sets one.
 */
@Entity
@Table(name = "user_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(unique = true, length = 254)
    private String email;

    /** E.164, e.g. +919876543210. */
    @Setter
    @Column(unique = true, length = 20)
    private String phone;

    @Setter
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Legacy {@code user_login.login_id}, set by the MySQL migration. */
    @Setter
    @Column(name = "legacy_login_id", unique = true)
    private Long legacyLoginId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static UserAccount create(String email, String phone, String passwordHash, Role role) {
        if (email == null && phone == null) {
            throw new IllegalArgumentException("An account needs an email or a phone number");
        }
        UserAccount account = new UserAccount();
        account.email = email;
        account.phone = phone;
        account.passwordHash = passwordHash;
        account.role = role;
        account.status = AccountStatus.ACTIVE;
        return account;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void registerFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }
}
