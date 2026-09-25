package com.spacz.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Admin-specific profile. Login and role live in auth-service ({@code adminUserId} = auth account ID).
 */
@Entity
@Table(name = "admin_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "admin_user_id", nullable = false, unique = true, updatable = false)
    private Long adminUserId;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 80)
    private String title;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AdminProfile create(Long adminUserId, String email) {
        AdminProfile profile = new AdminProfile();
        profile.adminUserId = adminUserId;
        profile.email = email;
        return profile;
    }
}
