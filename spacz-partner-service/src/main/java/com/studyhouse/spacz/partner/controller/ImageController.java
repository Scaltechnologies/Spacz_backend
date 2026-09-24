package com.studyhouse.spacz.partner.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studyhouse.spacz.partner.dto.request.ImageRequest;
import com.studyhouse.spacz.partner.dto.response.ImageResponse;
import com.studyhouse.spacz.partner.service.ImageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/images")
@Tag(name = "Image APIs", description = "Photos of a property (URLs only).")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an image to an existing property")
    public ResponseEntity<ImageResponse> createImage(@Valid @RequestBody ImageRequest request) {
        ImageResponse created = imageService.createImage(request);
        return ResponseEntity.created(Locations.of(created.imageId())).body(created);
    }

    @GetMapping
    @Operation(summary = "List all images")
    public List<ImageResponse> getAllImages() {
        return imageService.getAllImages();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an image by ID")
    public ImageResponse getImageById(@PathVariable Long id) {
        return imageService.getImageById(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an image")
    public ImageResponse updateImage(@PathVariable Long id, @Valid @RequestBody ImageRequest request) {
        return imageService.updateImage(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an image")
    public void deleteImage(@PathVariable Long id) {
        imageService.deleteImage(id);
    }
}
