package com.spacz.studyhall.dto.legacy;

import java.util.List;

/**
 * Response bodies of the legacy Partner API, field for field (parents nested, children as lists,
 * no back-references).
 */
public final class LegacyResponses {

    private LegacyResponses() {
    }

    public record SeatItem(Long seatId, String seatNumber, double seatPrice, boolean reserved) {
    }

    public record ImageItem(Long imageId, String imageUrl) {
    }

    public record BlockItem(Long blockId, String blockName, List<SeatItem> seats, double blockDailyPrice,
                            double blockMonthlyPrice) {
    }

    public record PropertyItem(Long propertyId, String propertyName, String address, String googleCoordinates,
                               List<ImageItem> images, List<BlockItem> blocks) {
    }

    public record OwnerSummary(Long ownerId, String ownerName, String ownerEmail, String ownerPhoneNumber,
                               String address, LegacyRefs.LoginRef userLogin) {
    }

    public record PropertySummary(Long propertyId, String propertyName, String address, String googleCoordinates,
                                  OwnerSummary owner) {
    }

    public record BlockSummary(Long blockId, String blockName, PropertySummary property, double blockDailyPrice,
                               double blockMonthlyPrice) {
    }

    public record OwnerResponse(Long ownerId, String ownerName, String ownerEmail, String ownerPhoneNumber,
                                String address, LegacyRefs.LoginRef userLogin, List<PropertyItem> properties) {
    }

    public record PropertyResponse(Long propertyId, String propertyName, String address, String googleCoordinates,
                                   OwnerSummary owner, List<ImageItem> images, List<BlockItem> blocks) {
    }

    public record BlockResponse(Long blockId, String blockName, PropertySummary property, List<SeatItem> seats,
                                double blockDailyPrice, double blockMonthlyPrice) {
    }

    public record SeatResponse(Long seatId, String seatNumber, BlockSummary block, double seatPrice, boolean reserved) {
    }

    public record AmenityResponse(Long amenityId, boolean ac, boolean wifi, boolean water, boolean lockers,
                                  boolean newspapers, BlockSummary block) {
    }

    public record ImageResponse(Long imageId, String imageUrl, PropertySummary property) {
    }
}
