package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.legacy.LegacyRequests;
import com.spacz.studyhall.dto.legacy.LegacyResponses;

import java.util.List;

/**
 * The legacy Partner-app API (owner / property / block / seat / amenity / image) on top of the new
 * model, scoped to the calling vendor.
 */
public interface LegacyPartnerService {

    LegacyResponses.OwnerResponse createOwner(Long vendorId, LegacyRequests.OwnerRequest request);

    List<LegacyResponses.OwnerResponse> owners(Long vendorId);

    LegacyResponses.OwnerResponse owner(Long vendorId, Long ownerId);

    LegacyResponses.OwnerResponse updateOwner(Long vendorId, Long ownerId, LegacyRequests.OwnerRequest request);

    void deleteOwner(Long vendorId, Long ownerId);

    List<LegacyResponses.PropertyResponse> ownerProperties(Long vendorId, Long ownerId);

    LegacyResponses.PropertyResponse createProperty(Long vendorId, LegacyRequests.PropertyRequest request);

    List<LegacyResponses.PropertyResponse> properties(Long vendorId);

    LegacyResponses.PropertyResponse property(Long vendorId, Long propertyId);

    LegacyResponses.PropertyResponse updateProperty(Long vendorId, Long propertyId, LegacyRequests.PropertyRequest request);

    void deleteProperty(Long vendorId, Long propertyId);

    List<LegacyResponses.BlockResponse> propertyBlocks(Long vendorId, Long propertyId);

    List<LegacyResponses.ImageResponse> propertyImages(Long vendorId, Long propertyId);

    LegacyResponses.BlockResponse createBlock(Long vendorId, LegacyRequests.BlockRequest request);

    List<LegacyResponses.BlockResponse> blocks(Long vendorId);

    LegacyResponses.BlockResponse block(Long vendorId, Long blockId);

    LegacyResponses.BlockResponse updateBlock(Long vendorId, Long blockId, LegacyRequests.BlockRequest request);

    void deleteBlock(Long vendorId, Long blockId);

    List<LegacyResponses.SeatResponse> blockSeats(Long vendorId, Long blockId);

    LegacyResponses.AmenityResponse blockAmenity(Long vendorId, Long blockId);

    LegacyResponses.SeatResponse createSeat(Long vendorId, LegacyRequests.SeatRequest request);

    List<LegacyResponses.SeatResponse> seats(Long vendorId);

    LegacyResponses.SeatResponse seat(Long vendorId, Long seatId);

    LegacyResponses.SeatResponse updateSeat(Long vendorId, Long seatId, LegacyRequests.SeatRequest request);

    void deleteSeat(Long vendorId, Long seatId);

    LegacyResponses.AmenityResponse createAmenity(Long vendorId, LegacyRequests.AmenityRequest request);

    List<LegacyResponses.AmenityResponse> amenities(Long vendorId);

    LegacyResponses.AmenityResponse amenity(Long vendorId, Long amenityId);

    LegacyResponses.AmenityResponse updateAmenity(Long vendorId, Long amenityId, LegacyRequests.AmenityRequest request);

    void deleteAmenity(Long vendorId, Long amenityId);

    LegacyResponses.ImageResponse createImage(Long vendorId, LegacyRequests.ImageRequest request);

    List<LegacyResponses.ImageResponse> images(Long vendorId);

    LegacyResponses.ImageResponse image(Long vendorId, Long imageId);

    LegacyResponses.ImageResponse updateImage(Long vendorId, Long imageId, LegacyRequests.ImageRequest request);

    void deleteImage(Long vendorId, Long imageId);
}
