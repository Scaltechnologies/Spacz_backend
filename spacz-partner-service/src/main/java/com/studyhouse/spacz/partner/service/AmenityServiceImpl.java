package com.studyhouse.spacz.partner.service;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.AmenityRequest;
import com.studyhouse.spacz.partner.dto.response.AmenityResponse;
import com.studyhouse.spacz.partner.entity.Amenity;
import com.studyhouse.spacz.partner.entity.Block;
import com.studyhouse.spacz.partner.exception.BadRequestException;
import com.studyhouse.spacz.partner.exception.ConflictException;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.AmenityRepository;
import com.studyhouse.spacz.partner.repository.BlockRepository;

@Service
@Transactional
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final BlockRepository blockRepository;

    public AmenityServiceImpl(AmenityRepository amenityRepository, BlockRepository blockRepository) {
        this.amenityRepository = amenityRepository;
        this.blockRepository = blockRepository;
    }

    @Override
    public AmenityResponse createAmenity(AmenityRequest request) {
        if (request.blockId() == null) {
            throw new BadRequestException("block.blockId is required");
        }
        Block block = findBlock(request.blockId());
        ensureBlockHasNoAmenity(block.getBlockId(), null);
        Amenity amenity = new Amenity();
        amenity.setBlock(block);
        applyFlags(amenity, request);
        return AmenityResponse.from(amenityRepository.saveAndFlush(amenity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AmenityResponse> getAllAmenities() {
        return amenityRepository.findAll(Sort.by("amenityId")).stream().map(AmenityResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AmenityResponse getAmenityById(Long id) {
        return AmenityResponse.from(findAmenity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public AmenityResponse getAmenityByBlockId(Long blockId) {
        findBlock(blockId);
        return amenityRepository.findByBlockBlockId(blockId).map(AmenityResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Amenity not found for block with ID: " + blockId));
    }

    // As in the legacy service, all flags are replaced with the values sent (not sent
    // = false). The legacy service also cleared the block when none was sent; here an
    // omitted block is left unchanged.
    @Override
    public AmenityResponse updateAmenity(Long id, AmenityRequest request) {
        Amenity amenity = findAmenity(id);
        if (request.blockId() != null) {
            Block block = findBlock(request.blockId());
            ensureBlockHasNoAmenity(block.getBlockId(), id);
            amenity.setBlock(block);
        }
        applyFlags(amenity, request);
        return AmenityResponse.from(amenityRepository.saveAndFlush(amenity));
    }

    @Override
    public void deleteAmenity(Long id) {
        amenityRepository.delete(findAmenity(id));
        amenityRepository.flush();
    }

    private static void applyFlags(Amenity amenity, AmenityRequest request) {
        amenity.setAc(Boolean.TRUE.equals(request.ac()));
        amenity.setWifi(Boolean.TRUE.equals(request.wifi()));
        amenity.setWater(Boolean.TRUE.equals(request.water()));
        amenity.setLockers(Boolean.TRUE.equals(request.lockers()));
        amenity.setNewspapers(Boolean.TRUE.equals(request.newspapers()));
    }

    // The database allows one amenity record per block (unique amenity.block_id).
    private void ensureBlockHasNoAmenity(Long blockId, Long allowedAmenityId) {
        amenityRepository.findByBlockBlockId(blockId)
                .filter(existing -> !Objects.equals(existing.getAmenityId(), allowedAmenityId))
                .ifPresent(existing -> {
                    throw new ConflictException("Block with ID: " + blockId + " already has an amenity record (ID: "
                            + existing.getAmenityId() + "). Update it with PUT /amenities/"
                            + existing.getAmenityId() + " instead.");
                });
    }

    private Amenity findAmenity(Long id) {
        return amenityRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Amenity", id));
    }

    private Block findBlock(Long id) {
        return blockRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Block", id));
    }
}
