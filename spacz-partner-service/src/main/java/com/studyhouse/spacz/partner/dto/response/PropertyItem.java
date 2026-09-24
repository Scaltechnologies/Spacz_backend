package com.studyhouse.spacz.partner.dto.response;

import java.util.List;

import com.studyhouse.spacz.partner.entity.Property;

/** A property inside its owner's {@code properties} list, with its images and blocks (no back-reference to the owner). */
public record PropertyItem(
        Long propertyId,
        String propertyName,
        String address,
        String googleCoordinates,
        List<ImageItem> images,
        List<BlockItem> blocks) {

    public static PropertyItem from(Property property) {
        return new PropertyItem(property.getPropertyId(), property.getPropertyName(), property.getAddress(),
                property.getGoogleCoordinates(),
                property.getImages().stream().map(ImageItem::from).toList(),
                property.getBlocks().stream().map(BlockItem::from).toList());
    }
}
