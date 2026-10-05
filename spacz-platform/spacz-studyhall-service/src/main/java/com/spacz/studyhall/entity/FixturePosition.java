package com.spacz.studyhall.entity;

/**
 * Where a fixture is mounted relative to the block grid (map seen from the entrance): NORTH = far
 * edge (above the last row), SOUTH = entrance edge (below row A), WEST / EAST = left / right edge,
 * CENTER = over the middle of the block (e.g. centralized AC).
 */
public enum FixturePosition {
    NORTH,
    SOUTH,
    EAST,
    WEST,
    CENTER
}
