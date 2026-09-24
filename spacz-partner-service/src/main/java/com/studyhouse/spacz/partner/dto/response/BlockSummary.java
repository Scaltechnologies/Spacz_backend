package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Block;

/** A block shown as the parent of another record: its fields and property, without its seat list. */
public record BlockSummary(
        Long blockId,
        String blockName,
        PropertySummary property,
        double blockDailyPrice,
        double blockMonthlyPrice) {

    public static BlockSummary from(Block block) {
        if (block == null) {
            return null;
        }
        return new BlockSummary(block.getBlockId(), block.getBlockName(), PropertySummary.from(block.getProperty()),
                block.getBlockDailyPrice(), block.getBlockMonthlyPrice());
    }
}
