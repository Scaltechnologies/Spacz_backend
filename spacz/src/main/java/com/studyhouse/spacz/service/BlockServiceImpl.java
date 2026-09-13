package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.Block;
import com.studyhouse.spacz.repository.BlockRepository;

@Service
public class BlockServiceImpl implements BlockService {
    @Autowired
    private BlockRepository blockRepository;

    @Override
    public Block saveBlock(Block block) {
        return blockRepository.save(block);
    }

    @Override
    public List<Block> getAllBlocks() {
        return blockRepository.findAll();
    }

    @Override
    public Optional<Block> getBlockById(Long id) {
        return blockRepository.findById(id);
    }

    @Override
    public Block updateBlock(Long id, Block block) {
        Block existingBlock = blockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Block not found"));
        //existingBlock.setName(block.getName());
        existingBlock.setProperty(block.getProperty());
        //existingBlock.setNumberOfRooms(block.getNumberOfRooms());
        return blockRepository.save(existingBlock);
    }

    @Override
    public void deleteBlock(Long id) {
        blockRepository.deleteById(id);
    }
}
