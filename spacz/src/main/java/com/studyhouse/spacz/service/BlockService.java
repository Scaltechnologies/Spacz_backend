package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.Block;

public interface BlockService {
    Block saveBlock(Block block);
    List<Block> getAllBlocks();
    Optional<Block> getBlockById(Long id);
    Block updateBlock(Long id, Block block);
    void deleteBlock(Long id);
}
