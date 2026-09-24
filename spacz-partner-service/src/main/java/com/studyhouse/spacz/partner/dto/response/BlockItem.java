package com.studyhouse.spacz.partner.dto.response;

import java.util.List;

import com.studyhouse.spacz.partner.entity.Block;

/** A block inside its property's {@code blocks} list, with its seats (no back-reference to the property). */
public record BlockItem(
        Long blockId,
        String blockName,
        List<SeatItem> seats,
        double blockDailyPrice,
        double blockMonthlyPrice) {

    public static BlockItem from(Block block) {
        return new BlockItem(block.getBlockId(), block.getBlockName(),
                block.getSeats().stream().map(SeatItem::from).toList(),
                block.getBlockDailyPrice(), block.getBlockMonthlyPrice());
    }
}
