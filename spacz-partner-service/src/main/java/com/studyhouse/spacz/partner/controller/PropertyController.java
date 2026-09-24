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

import com.studyhouse.spacz.partner.dto.request.PropertyRequest;
import com.studyhouse.spacz.partner.dto.response.BlockResponse;
import com.studyhouse.spacz.partner.dto.response.ImageResponse;
import com.studyhouse.spacz.partner.dto.response.PropertyResponse;
import com.studyhouse.spacz.partner.service.BlockService;
import com.studyhouse.spacz.partner.service.ImageService;
import com.studyhouse.spacz.partner.service.PropertyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/properties")
@Tag(name = "Property APIs", description = "Study-space locations owned by a partner")
public class PropertyController {

    private final PropertyService propertyService;
    private final BlockService blockService;
    private final ImageService imageService;

    public PropertyController(PropertyService propertyService, BlockService blockService, ImageService imageService) {
        this.propertyService = propertyService;
        this.blockService = blockService;
        this.imageService = imageService;
    }

    @PostMapping
    @Operation(summary = "Create a property for an existing owner", description = "Returns 200 OK (as before).")
    public PropertyResponse createProperty(@Valid @RequestBody PropertyRequest request) {
        return propertyService.createProperty(request);
    }

    @GetMapping
    @Operation(summary = "List all properties")
    public List<PropertyResponse> getAllProperties() {
        return propertyService.getAllProperties();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a property by ID")
    public PropertyResponse getPropertyById(@PathVariable Long id) {
        return propertyService.getPropertyById(id);
    }

    @GetMapping("/{id}/blocks")
    @Operation(summary = "List the blocks of a property")
    public List<BlockResponse> getPropertyBlocks(@PathVariable Long id) {
        return blockService.getBlocksByPropertyId(id);
    }

    @GetMapping("/{id}/images")
    @Operation(summary = "List the images of a property")
    public List<ImageResponse> getPropertyImages(@PathVariable Long id) {
        return imageService.getImagesByPropertyId(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a property")
    public PropertyResponse updateProperty(@PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
        return propertyService.updateProperty(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a property",
            description = "Also deletes the property's blocks, seats, amenities and images. 204 also for unknown IDs (as before).")
    public void deleteProperty(@PathVariable Long id) {
        propertyService.deleteProperty(id);
    }
}
