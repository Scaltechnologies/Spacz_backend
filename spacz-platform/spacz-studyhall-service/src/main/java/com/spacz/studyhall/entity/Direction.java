package com.spacz.studyhall.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.spacz.studyhall.exception.BusinessRuleException;

import java.util.Locale;
import java.util.Optional;

/**
 * Compass orientation on the block map. The map is seen from the entrance: NORTH = towards the far
 * side (higher rows), SOUTH = towards the entrance/user side (row 1 = A), WEST / EAST = towards lower /
 * higher columns. For a SEAT it is the way the occupant faces; for an ENTRANCE / EXIT the way it opens;
 * for stairs/lift the way they lead; for AC the airflow (CENTER = centralized). Only
 * {@link LayoutCellType#isDirectional()} types (and fixtures) have one. Diagonals can be added later
 * (V8 check constraint + {@link #requireValidFor}).
 */
public enum Direction {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    CENTER;

    /**
     * "NORTH", "north", "N", and the pre-V8 names UP / DOWN / LEFT / RIGHT (U/D/L/R) → the direction.
     */
    public static Optional<Direction> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NORTH", "N", "UP", "U" -> NORTH;
            case "SOUTH", "S", "DOWN", "D" -> SOUTH;
            case "EAST", "E", "RIGHT", "R" -> EAST;
            case "WEST", "W", "LEFT", "L" -> WEST;
            case "CENTER", "CENTRE", "C" -> CENTER;
            default -> null;
        });
    }

    /** JSON input accepts the compass names and the legacy UP/DOWN/LEFT/RIGHT aliases. */
    @JsonCreator
    public static Direction fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parse(value).orElseThrow(() -> new IllegalArgumentException("Unknown direction " + value));
    }

    /** Fails when {@code direction} makes no sense for {@code type}. Null is always allowed. */
    public static void requireValidFor(LayoutCellType type, Direction direction) {
        if (direction == null) {
            return;
        }
        if (!type.isDirectional()) {
            throw new BusinessRuleException("INVALID_LAYOUT_CELL", type + " cells have no direction");
        }
        if (direction == CENTER && type != LayoutCellType.AC) {
            throw new BusinessRuleException("INVALID_LAYOUT_CELL", "Only AC can be CENTER (centralized)");
        }
    }
}
