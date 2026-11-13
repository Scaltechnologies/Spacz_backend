package com.spacz.studyhall.dto.seat;

import com.spacz.studyhall.entity.LayoutType;

import java.util.List;

/**
 * @param cells every position of the rows × columns grid in row-major order (EMPTY included)
 */
public record LayoutResponse(LayoutType layoutType, int rows, int columns, long version, List<LayoutCellResponse> cells,
                             List<FixtureResponse> fixtures) {
}
