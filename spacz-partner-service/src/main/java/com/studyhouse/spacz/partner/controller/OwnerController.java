package com.studyhouse.spacz.partner.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.partner.dto.request.OwnerRequest;
import com.studyhouse.spacz.partner.dto.response.OwnerResponse;
import com.studyhouse.spacz.partner.dto.response.PropertyResponse;
import com.studyhouse.spacz.partner.service.OwnerService;
import com.studyhouse.spacz.partner.service.PropertyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/owners")
@Tag(name = "Owner APIs", description = "Partner/owner profiles")
public class OwnerController {

    private final OwnerService ownerService;
    private final PropertyService propertyService;

    public OwnerController(OwnerService ownerService, PropertyService propertyService) {
        this.ownerService = ownerService;
        this.propertyService = propertyService;
    }

    @PostMapping
    @Operation(summary = "Create an owner", description = "Returns 200 OK (as before).")
    public OwnerResponse createOwner(@Valid @RequestBody OwnerRequest request) {
        return ownerService.createOwner(request);
    }

    @GetMapping
    @Operation(summary = "List all owners")
    public List<OwnerResponse> getAllOwners() {
        return ownerService.getAllOwners();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an owner by ID")
    public OwnerResponse getOwnerById(@PathVariable Long id) {
        return ownerService.getOwnerById(id);
    }

    @GetMapping("/{id}/properties")
    @Operation(summary = "List the properties of an owner")
    public List<PropertyResponse> getOwnerProperties(@PathVariable Long id) {
        return propertyService.getPropertiesByOwnerId(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an owner")
    public OwnerResponse updateOwner(@PathVariable Long id, @Valid @RequestBody OwnerRequest request) {
        return ownerService.updateOwner(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an owner",
            description = "Also deletes all of the owner's properties, blocks, seats, amenities and images. 204 also for unknown IDs (as before).")
    public void deleteOwner(@PathVariable Long id) {
        ownerService.deleteOwner(id);
    }
}
