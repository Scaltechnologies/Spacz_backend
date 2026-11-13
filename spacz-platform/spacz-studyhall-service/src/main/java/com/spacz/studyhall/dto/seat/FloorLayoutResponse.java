package com.spacz.studyhall.dto.seat;

import java.util.List;

public record FloorLayoutResponse(Long id, int floorNumber, String name, String description, int displayOrder,
                                  List<BlockLayoutResponse> blocks) {
}
