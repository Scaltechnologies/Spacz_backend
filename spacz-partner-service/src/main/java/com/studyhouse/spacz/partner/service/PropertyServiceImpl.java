package com.studyhouse.spacz.partner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.PropertyRequest;
import com.studyhouse.spacz.partner.dto.response.PropertyResponse;
import com.studyhouse.spacz.partner.entity.Owner;
import com.studyhouse.spacz.partner.entity.Property;
import com.studyhouse.spacz.partner.exception.BadRequestException;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.AmenityRepository;
import com.studyhouse.spacz.partner.repository.OwnerRepository;
import com.studyhouse.spacz.partner.repository.PropertyRepository;

@Service
@Transactional
public class PropertyServiceImpl implements PropertyService {

    private final PropertyRepository propertyRepository;
    private final OwnerRepository ownerRepository;
    private final AmenityRepository amenityRepository;

    public PropertyServiceImpl(PropertyRepository propertyRepository, OwnerRepository ownerRepository,
            AmenityRepository amenityRepository) {
        this.propertyRepository = propertyRepository;
        this.ownerRepository = ownerRepository;
        this.amenityRepository = amenityRepository;
    }

    @Override
    public PropertyResponse createProperty(PropertyRequest request) {
        if (request.ownerId() == null) {
            throw new BadRequestException("owner.ownerId is required");
        }
        Property property = new Property();
        property.setOwner(findOwner(request.ownerId()));
        property.setPropertyName(request.propertyName());
        property.setAddress(request.address());
        property.setGoogleCoordinates(request.googleCoordinates());
        return PropertyResponse.from(propertyRepository.saveAndFlush(property));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getAllProperties() {
        return propertyRepository.findAll(Sort.by("propertyId")).stream().map(PropertyResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PropertyResponse getPropertyById(Long id) {
        return PropertyResponse.from(findProperty(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyResponse> getPropertiesByOwnerId(Long ownerId) {
        findOwner(ownerId);
        return propertyRepository.findByOwnerOwnerIdOrderByPropertyIdAsc(ownerId).stream()
                .map(PropertyResponse::from).toList();
    }

    // As in the legacy service, `address` is replaced with the value sent. The legacy
    // service ignored name, coordinates and owner (those lines were commented out);
    // here they are saved when sent and left unchanged when omitted.
    @Override
    public PropertyResponse updateProperty(Long id, PropertyRequest request) {
        Property property = findProperty(id);
        property.setAddress(request.address());
        if (request.propertyName() != null) {
            property.setPropertyName(request.propertyName());
        }
        if (request.googleCoordinates() != null) {
            property.setGoogleCoordinates(request.googleCoordinates());
        }
        if (request.ownerId() != null) {
            property.setOwner(findOwner(request.ownerId()));
        }
        return PropertyResponse.from(propertyRepository.saveAndFlush(property));
    }

    // Deletes the property and, via cascade, its blocks, seats and images, plus the
    // amenities of those blocks. Unknown IDs are ignored (as before).
    @Override
    public void deleteProperty(Long id) {
        propertyRepository.findById(id).ifPresent(property -> {
            amenityRepository.deleteByPropertyId(id);
            propertyRepository.delete(property);
            propertyRepository.flush();
        });
    }

    private Property findProperty(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }

    private Owner findOwner(Long id) {
        return ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Owner", id));
    }
}
