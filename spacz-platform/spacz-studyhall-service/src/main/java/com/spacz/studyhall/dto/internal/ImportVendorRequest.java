package com.spacz.studyhall.dto.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One legacy owner with everything below it, as read from MySQL `spacz` by the migration tool.
 */
public record ImportVendorRequest(@NotNull Long vendorId, @NotNull Long legacyOwnerId, String ownerName,
                                  String ownerEmail, String ownerPhoneNumber, String address,
                                  List<@Valid Property> properties) {

    public record Property(@NotNull Long legacyPropertyId, String propertyName, String address,
                           String googleCoordinates, List<@Valid Image> images, List<@Valid Block> blocks) {
    }

    public record Image(@NotNull Long legacyImageId, String imageUrl) {
    }

    public record Block(@NotNull Long legacyBlockId, String blockName, Double blockDailyPrice, Double blockMonthlyPrice,
                        Amenity amenity, List<@Valid Seat> seats) {
    }

    public record Amenity(boolean ac, boolean wifi, boolean water, boolean lockers, boolean newspapers) {
    }

    public record Seat(@NotNull Long legacySeatId, String seatNumber, boolean reserved, Double seatPrice) {
    }
}
