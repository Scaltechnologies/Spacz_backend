package com.spacz.user.entity;

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

import java.time.Instant;
import java.time.LocalDate;

/**
 * A student's profile. {@code userId} is the auth-service account ID (no FK: different database).
 * {@code email}/{@code phone} are display copies taken at registration; auth-service remains the
 * source of truth for login identifiers. Phone-OTP students may have no email.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Column(length = 254)
    private String email;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 500)
    private String bio;

    @Column(name = "education_level", length = 80)
    private String educationLevel;

    @Column(name = "preferred_city", length = 80)
    private String preferredCity;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_study_slot", length = 20)
    private StudySlot preferredStudySlot;

    @Column(name = "daily_study_hours_goal")
    private Integer dailyStudyHoursGoal;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    public static UserProfile create(Long userId, String email, String firstName) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.email = email;
        profile.firstName = firstName;
        return profile;
    }
}
