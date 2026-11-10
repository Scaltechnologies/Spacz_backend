package com.spacz.studyhall.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * What occupies one (row, column) position of a block layout. Only {@link #SEAT} cells reference a
 * {@link Seat}; every other type is a physical feature that can never be booked.
 */
public enum LayoutCellType {
    EMPTY,
    SEAT,
    WALKWAY,
    ENTRANCE,
    EXIT,
    TABLE,
    WALL,
    PILLAR,
    RECEPTION,
    STAIRS,
    LIFT,
    RESTROOM,
    /** Air conditioner: a fixture at a position (never a seat or walkway); its direction is the airflow. */
    AC,
    OTHER;

    /** Types whose {@link Direction} is meaningful (facing, opening, airflow). */
    public boolean isDirectional() {
        return this == SEAT || this == ENTRANCE || this == EXIT || this == AC || this == STAIRS || this == LIFT;
    }

    /** "Entrance", "main entrance", "RESTROOM" → the matching type, if any. */
    public static Optional<LayoutCellType> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (LayoutCellType type : values()) {
            if (type.name().equals(normalized)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
