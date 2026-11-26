package com.spacz.studyhall.dto.seat;

import java.util.Map;

/**
 * @param applied  number of operations applied (all of them: a batch never applies partially)
 * @param floorIds ids of the floors created in the batch, by their ref
 * @param blockIds ids of the blocks created in the batch, by their ref
 */
public record SeatingBatchResponse(int applied, Map<String, Long> floorIds, Map<String, Long> blockIds) {
}
