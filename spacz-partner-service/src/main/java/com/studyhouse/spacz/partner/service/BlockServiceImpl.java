package com.studyhouse.spacz.partner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.BlockRequest;
import com.studyhouse.spacz.partner.dto.response.BlockResponse;
import com.studyhouse.spacz.partner.entity.Block;
import com.studyhouse.spacz.partner.entity.Property;
import com.studyhouse.spacz.partner.exception.BadRequestException;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.AmenityRepository;
import com.studyhouse.spacz.partner.repository.BlockRepository;
import com.studyhouse.spacz.partner.repository.PropertyRepository;

@Service
@Transactional
public class BlockServiceImpl implements BlockService {

    private final BlockRepository blockRepository;
    private final PropertyRepository propertyRepository;
    private final AmenityRepository amenityRepository;

    public BlockServiceImpl(BlockRepository blockRepository, PropertyRepository propertyRepository,
            AmenityRepository amenityRepository) {
        this.blockRepository = blockRepository;
        this.propertyRepository = propertyRepository;
        this.amenityRepository = amenityRepository;
    }

    @Override
    public BlockResponse createBlock(BlockRequest request) {
        if (request.propertyId() == null) {
            throw new BadRequestException("property.propertyId is required");
        }
        Block block = new Block();
        block.setProperty(findProperty(request.propertyId()));
        block.setBlockName(request.blockName());
        block.setBlockDailyPrice(request.blockDailyPrice() == null ? 0 : request.blockDailyPrice());
        block.setBlockMonthlyPrice(request.blockMonthlyPrice() == null ? 0 : request.blockMonthlyPrice());
        return BlockResponse.from(blockRepository.saveAndFlush(block));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockResponse> getAllBlocks() {
        return blockRepository.findAll(Sort.by("blockId")).stream().map(BlockResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BlockResponse getBlockById(Long id) {
        return BlockResponse.from(findBlock(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockResponse> getBlocksByPropertyId(Long propertyId) {
        findProperty(propertyId);
        return blockRepository.findByPropertyPropertyIdOrderByBlockIdAsc(propertyId).stream()
                .map(BlockResponse::from).toList();
    }

    // As in the legacy service, the `property` sent is applied (it must exist). The
    // legacy service also cleared the property when none was sent, and ignored name
    // and prices (commented out); here an omitted property/name/price is left unchanged
    // and a sent one is saved.
    @Override
    public BlockResponse updateBlock(Long id, BlockRequest request) {
        Block block = findBlock(id);
        if (request.propertyId() != null) {
            block.setProperty(findProperty(request.propertyId()));
        }
        if (request.blockName() != null) {
            block.setBlockName(request.blockName());
        }
        if (request.blockDailyPrice() != null) {
            block.setBlockDailyPrice(request.blockDailyPrice());
        }
        if (request.blockMonthlyPrice() != null) {
            block.setBlockMonthlyPrice(request.blockMonthlyPrice());
        }
        return BlockResponse.from(blockRepository.saveAndFlush(block));
    }

    // Deletes the block and, via cascade, its seats, plus its amenity record.
    // Unknown IDs are ignored (as before).
    @Override
    public void deleteBlock(Long id) {
        blockRepository.findById(id).ifPresent(block -> {
            amenityRepository.deleteByBlockId(id);
            blockRepository.delete(block);
            blockRepository.flush();
        });
    }

    private Block findBlock(Long id) {
        return blockRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Block", id));
    }

    private Property findProperty(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }
}
