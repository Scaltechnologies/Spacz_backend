package com.spacz.studyhall.entity;

/**
 * Environmental fixtures annotated on a block layout. They sit on an edge or the centre of the block
 * and never take a grid cell, so they can never become seats or bookable. Extend with new values
 * (and the V8 check constraint) when more fixture kinds are needed.
 */
public enum FixtureType {
    /** Air conditioner; its direction is the airflow (CENTER = centralized). */
    AC
}
