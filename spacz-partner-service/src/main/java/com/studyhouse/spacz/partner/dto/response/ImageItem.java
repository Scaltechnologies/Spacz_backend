package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Image;

/** An image inside its property's {@code images} list (no back-reference to the property). */
public record ImageItem(
        Long imageId,
        String imageUrl) {

    public static ImageItem from(Image image) {
        return new ImageItem(image.getImageId(), image.getImageUrl());
    }
}
