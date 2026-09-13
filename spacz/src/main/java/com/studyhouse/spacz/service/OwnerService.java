package com.studyhouse.spacz.service;

import java.util.List;
import java.util.Optional;

import com.studyhouse.spacz.entity.Owner;

public interface OwnerService {
    Owner saveOwner(Owner owner);
    List<Owner> getAllOwners();
    Optional<Owner> getOwnerById(Long id);
    Owner updateOwner(Long id, Owner owner);
    void deleteOwner(Long id);
}
