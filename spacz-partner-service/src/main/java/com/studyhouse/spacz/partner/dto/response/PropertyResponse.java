package com.studyhouse.spacz.partner.dto.response;

import java.util.List;

import com.studyhouse.spacz.partner.entity.Property;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Property with its owner, images and blocks (with seats). Same fields as the legacy API; "
        + "nested records just don't point back to their parent.")
public record PropertyResponse(
        Long propertyId,
        String propertyName,
        String address,
        String googleCoordinates,
        OwnerSummary owner,
        List<ImageItem> images,
        List<BlockItem> blocks) {

    public static PropertyResponse from(Property property) {
        return new PropertyResponse(property.getPropertyId(), property.getPropertyName(), property.getAddress(),
                property.getGoogleCoordinates(), OwnerSummary.from(property.getOwner()),
                property.getImages().stream().map(ImageItem::from).toList(),
                property.getBlocks().stream().map(BlockItem::from).toList());
    }
}
