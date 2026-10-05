package com.spacz.studyhall.dto.hall;

/**
 * A city that has live study halls, for the student app's location filter.
 */
public record LocationResponse(String city, String state, long hallCount) {
}
