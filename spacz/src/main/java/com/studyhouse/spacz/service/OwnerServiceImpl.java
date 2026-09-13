package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.studyhouse.spacz.entity.Owner;
import com.studyhouse.spacz.repository.OwnerRepository;

@Service
public class OwnerServiceImpl implements OwnerService {
    @Autowired
    private OwnerRepository ownerRepository;

    @Override
    public Owner saveOwner(Owner owner) {
        return ownerRepository.save(owner);
    }

    @Override
    public List<Owner> getAllOwners() {
        return ownerRepository.findAll();
    }

    @Override
    public Optional<Owner> getOwnerById(Long id) {
        return ownerRepository.findById(id);
    }

    @Override
    public Owner updateOwner(Long id, Owner owner) {
        Owner existingOwner = ownerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Owner not found"));
        existingOwner.setOwnerName(owner.getOwnerName());
        existingOwner.setOwnerPhoneNumber(owner.getOwnerPhoneNumber());
        existingOwner.setOwnerEmail(owner.getOwnerEmail());
        existingOwner.setAddress(owner.getAddress());
        return ownerRepository.save(existingOwner);
    }

    @Override
    public void deleteOwner(Long id) {
        ownerRepository.deleteById(id);
    }
}
