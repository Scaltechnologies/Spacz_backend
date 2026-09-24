package com.studyhouse.spacz.partner.dto.response;

import java.util.List;

import com.studyhouse.spacz.partner.entity.Block;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Block with its property (and owner) and its seats. Same fields as the legacy API; "
        + "nested records just don't point back to their parent.")
public record BlockResponse(
        Long blockId,
        String blockName,
        PropertySummary property,
        List<SeatItem> seats,
        double blockDailyPrice,
        double blockMonthlyPrice) {

    public static BlockResponse from(Block block) {
        return new BlockResponse(block.getBlockId(), block.getBlockName(), PropertySummary.from(block.getProperty()),
                block.getSeats().stream().map(SeatItem::from).toList(),
                block.getBlockDailyPrice(), block.getBlockMonthlyPrice());
    }
}
