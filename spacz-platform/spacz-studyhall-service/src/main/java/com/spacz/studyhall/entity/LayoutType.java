package com.spacz.studyhall.entity;

/**
 * How a block layout was produced.
 * GRID = a plain rows × columns grid of seats (with gaps); CUSTOM = built from a template or with
 * physical features (walkways, entrances ...) placed by the vendor.
 */
public enum LayoutType {
    GRID,
    CUSTOM
}
