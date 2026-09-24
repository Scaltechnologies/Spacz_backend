package com.spacz.studyhall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A study hall photo (URL only; files live in object storage / CDN). Legacy: the {@code image} table.
 */
@Entity
@Table(name = "study_hall_images")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyHallImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false)
    private StudyHall studyHall;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 200)
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_cover", nullable = false)
    private boolean cover;

    @Column(name = "legacy_image_id", unique = true)
    private Long legacyImageId;

    public static StudyHallImage of(StudyHall hall, String url, String caption, int displayOrder, boolean cover) {
        StudyHallImage image = new StudyHallImage();
        image.studyHall = hall;
        image.url = url;
        image.caption = caption;
        image.displayOrder = displayOrder;
        image.cover = cover;
        return image;
    }
}
