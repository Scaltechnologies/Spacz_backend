package com.studyhouse.spacz.partner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studyhouse.spacz.partner.dto.request.ImageRequest;
import com.studyhouse.spacz.partner.dto.response.ImageResponse;
import com.studyhouse.spacz.partner.entity.Image;
import com.studyhouse.spacz.partner.entity.Property;
import com.studyhouse.spacz.partner.exception.BadRequestException;
import com.studyhouse.spacz.partner.exception.ResourceNotFoundException;
import com.studyhouse.spacz.partner.repository.ImageRepository;
import com.studyhouse.spacz.partner.repository.PropertyRepository;

@Service
@Transactional
public class ImageServiceImpl implements ImageService {

    private final ImageRepository imageRepository;
    private final PropertyRepository propertyRepository;

    public ImageServiceImpl(ImageRepository imageRepository, PropertyRepository propertyRepository) {
        this.imageRepository = imageRepository;
        this.propertyRepository = propertyRepository;
    }

    @Override
    public ImageResponse createImage(ImageRequest request) {
        if (request.propertyId() == null) {
            throw new BadRequestException("property.propertyId is required");
        }
        Image image = new Image();
        image.setProperty(findProperty(request.propertyId()));
        image.setImageUrl(request.imageUrl());
        return ImageResponse.from(imageRepository.saveAndFlush(image));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageResponse> getAllImages() {
        return imageRepository.findAll(Sort.by("imageId")).stream().map(ImageResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ImageResponse getImageById(Long id) {
        return ImageResponse.from(findImage(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImageResponse> getImagesByPropertyId(Long propertyId) {
        findProperty(propertyId);
        return imageRepository.findByPropertyPropertyIdOrderByImageIdAsc(propertyId).stream()
                .map(ImageResponse::from).toList();
    }

    // As in the legacy service, the URL is replaced with the value sent. The legacy
    // service also cleared the property when none was sent; here an omitted property
    // is left unchanged.
    @Override
    public ImageResponse updateImage(Long id, ImageRequest request) {
        Image image = findImage(id);
        if (request.propertyId() != null) {
            image.setProperty(findProperty(request.propertyId()));
        }
        image.setImageUrl(request.imageUrl());
        return ImageResponse.from(imageRepository.saveAndFlush(image));
    }

    @Override
    public void deleteImage(Long id) {
        imageRepository.delete(findImage(id));
        imageRepository.flush();
    }

    private Image findImage(Long id) {
        return imageRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Image", id));
    }

    private Property findProperty(Long id) {
        return propertyRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Property", id));
    }
}
