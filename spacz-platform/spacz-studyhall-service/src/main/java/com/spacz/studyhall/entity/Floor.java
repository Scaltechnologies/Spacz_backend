package com.spacz.studyhall.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical floor of a study hall; it groups the hall's blocks. Floor numbers are unique per hall
 * (0 = ground, negative = basement).
 */
@Entity
@Table(name = "floors")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Floor {

    public static final int GROUND = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_hall_id", nullable = false, updatable = false)
    private StudyHall studyHall;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Read side only: blocks are created and removed through {@link StudyHall#getBlocks()}. */
    @Setter(AccessLevel.NONE)
    @OrderBy("displayOrder ASC, id ASC")
    @OneToMany(mappedBy = "floor")
    private List<Block> blocks = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Floor of(StudyHall hall, int floorNumber, String name, int displayOrder) {
        Floor floor = new Floor();
        floor.studyHall = hall;
        floor.floorNumber = floorNumber;
        floor.name = name;
        floor.displayOrder = displayOrder;
        return floor;
    }
}
