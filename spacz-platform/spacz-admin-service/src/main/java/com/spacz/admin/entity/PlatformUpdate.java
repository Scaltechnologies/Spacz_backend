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
 * An announcement for students ("Updates" tab). Visible once published and until archived.
 */
@Entity
@Table(name = "platform_updates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private boolean published;

    @Setter(AccessLevel.NONE)
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private boolean archived;

    @Setter(AccessLevel.NONE)
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PlatformUpdate create(Long createdBy) {
        PlatformUpdate update = new PlatformUpdate();
        update.createdBy = createdBy;
        return update;
    }

    public void publish(Instant now) {
        if (!published) {
            published = true;
            publishedAt = now;
        }
    }

    public void unpublish() {
        published = false;
        publishedAt = null;
    }
}
