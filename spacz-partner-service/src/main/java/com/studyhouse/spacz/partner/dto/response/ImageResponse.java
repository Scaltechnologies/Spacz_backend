package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Image;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Property image, with its property (and owner). Same fields as the legacy API.")
public record ImageResponse(
        Long imageId,
        String imageUrl,
        PropertySummary property) {

    public static ImageResponse from(Image image) {
        return new ImageResponse(image.getImageId(), image.getImageUrl(), PropertySummary.from(image.getProperty()));
    }
}
