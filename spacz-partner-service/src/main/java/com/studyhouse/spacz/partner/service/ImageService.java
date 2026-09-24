package com.studyhouse.spacz.partner.service;

import java.util.List;

import com.studyhouse.spacz.partner.dto.request.ImageRequest;
import com.studyhouse.spacz.partner.dto.response.ImageResponse;

public interface ImageService {

    ImageResponse createImage(ImageRequest request);

    List<ImageResponse> getAllImages();

    ImageResponse getImageById(Long id);

    List<ImageResponse> getImagesByPropertyId(Long propertyId);

    ImageResponse updateImage(Long id, ImageRequest request);

    void deleteImage(Long id);
}
