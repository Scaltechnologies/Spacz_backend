package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Amenity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Amenities of a block, with the block (and its property and owner). Same fields as the legacy API.")
public record AmenityResponse(
        Long amenityId,
        boolean ac,
        boolean wifi,
        boolean water,
        boolean lockers,
        boolean newspapers,
        BlockSummary block) {

    public static AmenityResponse from(Amenity amenity) {
        return new AmenityResponse(amenity.getAmenityId(), amenity.isAc(), amenity.isWifi(), amenity.isWater(),
                amenity.isLockers(), amenity.isNewspapers(), BlockSummary.from(amenity.getBlock()));
    }
}
