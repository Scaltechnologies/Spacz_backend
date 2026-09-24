package com.studyhouse.spacz.partner.dto.response;

import com.studyhouse.spacz.partner.entity.Property;

/** A property shown as the parent of another record: its fields and owner, without its image/block lists. */
public record PropertySummary(
        Long propertyId,
        String propertyName,
        String address,
        String googleCoordinates,
        OwnerSummary owner) {

    public static PropertySummary from(Property property) {
        if (property == null) {
            return null;
        }
        return new PropertySummary(property.getPropertyId(), property.getPropertyName(), property.getAddress(),
                property.getGoogleCoordinates(), OwnerSummary.from(property.getOwner()));
    }
}
