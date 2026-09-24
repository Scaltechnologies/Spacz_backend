package com.spacz.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A one-time code sent by SMS. Only a keyed hash of the code is stored; it expires quickly and
 * allows a limited number of attempts.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, updatable = false)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 64, updatable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "requested_ip", length = 64, updatable = false)
    private String requestedIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static OtpChallenge issue(String phone, String codeHash, Instant expiresAt, String requestedIp) {
        OtpChallenge challenge = new OtpChallenge();
        challenge.phone = phone;
        challenge.codeHash = codeHash;
        challenge.expiresAt = expiresAt;
        challenge.requestedIp = requestedIp;
        return challenge;
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }

    public void registerFailedAttempt() {
        attempts++;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }
}
