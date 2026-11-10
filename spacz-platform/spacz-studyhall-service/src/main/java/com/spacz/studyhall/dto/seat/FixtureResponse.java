package com.spacz.studyhall.dto.seat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spacz.studyhall.entity.Direction;
import com.spacz.studyhall.entity.FixturePosition;
import com.spacz.studyhall.entity.FixtureType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FixtureResponse(Long id, FixtureType type, FixturePosition position, Integer offset, Direction direction,
                              String label) {
}
