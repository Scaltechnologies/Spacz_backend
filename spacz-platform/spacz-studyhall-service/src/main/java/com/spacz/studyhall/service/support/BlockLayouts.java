package com.spacz.studyhall.service.support;

import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.BlockLayout;
import com.spacz.studyhall.entity.LayoutType;
import com.spacz.studyhall.repository.BlockLayoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Loads a block's layout (same id as the block). Every block gets one when created and V6 created one
 * for existing blocks; a missing layout is rebuilt as a plain grid of the block's seats.
 */
@Component
@RequiredArgsConstructor
public class BlockLayouts {

    private final BlockLayoutRepository layoutRepository;

    /** Call inside a read-write transaction. */
    public BlockLayout of(Block block) {
        return layoutRepository.findById(block.getId())
                .orElseGet(() -> layoutRepository.save(BlockLayout.of(block, LayoutType.GRID)));
    }
}
