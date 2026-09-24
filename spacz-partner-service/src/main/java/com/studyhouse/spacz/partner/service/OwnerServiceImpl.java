package com.studyhouse.spacz.partner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.OwnerRequest;
import com.studyhouse.spacz.partner.dto.response.OwnerResponse;
import com.studyhouse.spacz.partner.entity.Owner;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.AmenityRepository;
import com.studyhouse.spacz.partner.repository.OwnerRepository;

@Service
@Transactional
public class OwnerServiceImpl implements OwnerService {

    private final OwnerRepository ownerRepository;
    private final AmenityRepository amenityRepository;

    public OwnerServiceImpl(OwnerRepository ownerRepository, AmenityRepository amenityRepository) {
        this.ownerRepository = ownerRepository;
        this.amenityRepository = amenityRepository;
    }

    @Override
    public OwnerResponse createOwner(OwnerRequest request) {
        Owner owner = new Owner();
        owner.setOwnerName(request.ownerName());
        owner.setOwnerEmail(request.ownerEmail());
        owner.setOwnerPhoneNumber(request.ownerPhoneNumber());
        owner.setAddress(request.address());
        owner.setLoginId(request.loginId());
        return OwnerResponse.from(ownerRepository.saveAndFlush(owner));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OwnerResponse> getAllOwners() {
        return ownerRepository.findAll(Sort.by("ownerId")).stream().map(OwnerResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerResponse getOwnerById(Long id) {
        return OwnerResponse.from(findOwner(id));
    }

    // Same as the legacy service: name, phone, email and address are replaced with
    // the values sent; the login link is not changed by an update.
    @Override
    public OwnerResponse updateOwner(Long id, OwnerRequest request) {
        Owner owner = findOwner(id);
        owner.setOwnerName(request.ownerName());
        owner.setOwnerPhoneNumber(request.ownerPhoneNumber());
        owner.setOwnerEmail(request.ownerEmail());
        owner.setAddress(request.address());
        return OwnerResponse.from(ownerRepository.saveAndFlush(owner));
    }

    // Deletes the owner and, via cascade, all of its properties, blocks, seats and
    // images, plus the amenities of those blocks. Unknown IDs are ignored (as before).
    @Override
    public void deleteOwner(Long id) {
        ownerRepository.findById(id).ifPresent(owner -> {
            amenityRepository.deleteByOwnerId(id);
            ownerRepository.delete(owner);
            ownerRepository.flush();
        });
    }

    private Owner findOwner(Long id) {
        return ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Owner", id));
    }
}
