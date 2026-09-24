package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.legacy.LegacyRefs;
import com.spacz.studyhall.dto.legacy.LegacyResponses;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.entity.VendorProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps the new model onto the legacy Partner API's JSON (owner / property / block / seat / amenity / image).
 * IDs are the new IDs: ownerId = vendor profile ID, propertyId = study hall ID, amenityId = block ID.
 */
@Component
public class LegacyMapper {

    /** Catalog codes that stand for the legacy per-block amenity flags. */
    public static final String AC = "AC";
    public static final String WIFI = "WIFI";
    public static final String WATER = "WATER";
    public static final String LOCKER = "LOCKER";
    public static final String NEWSPAPER = "NEWSPAPER";
    public static final Set<String> LEGACY_AMENITY_CODES = Set.of(AC, WIFI, WATER, LOCKER, NEWSPAPER);

    public LegacyResponses.OwnerSummary owner(VendorProfile v) {
        return new LegacyResponses.OwnerSummary(v.getId(), ownerName(v), v.getEmail(), v.getPhone(), v.getAddressLine(),
                LegacyRefs.LoginRef.of(v.getVendorId()));
    }

    public LegacyResponses.OwnerResponse ownerWithProperties(VendorProfile v, List<StudyHall> halls) {
        return new LegacyResponses.OwnerResponse(v.getId(), ownerName(v), v.getEmail(), v.getPhone(), v.getAddressLine(),
                LegacyRefs.LoginRef.of(v.getVendorId()), halls.stream().map(this::propertyItem).toList());
    }

    public LegacyResponses.PropertySummary property(StudyHall h) {
        return new LegacyResponses.PropertySummary(h.getId(), h.getName(), h.getAddressLine(), coordinates(h),
                owner(h.getVendor()));
    }

    public LegacyResponses.PropertyResponse propertyWithChildren(StudyHall h) {
        return new LegacyResponses.PropertyResponse(h.getId(), h.getName(), h.getAddressLine(), coordinates(h),
                owner(h.getVendor()), h.getImages().stream().map(this::imageItem).toList(),
                h.getBlocks().stream().map(this::blockItem).toList());
    }

    public LegacyResponses.BlockSummary block(Block b) {
        return new LegacyResponses.BlockSummary(b.getId(), b.getName(), property(b.getStudyHall()),
                price(b.effectiveDailyPrice()), price(b.effectiveMonthlyPrice()));
    }

    public LegacyResponses.BlockResponse blockWithSeats(Block b) {
        return new LegacyResponses.BlockResponse(b.getId(), b.getName(), property(b.getStudyHall()),
                b.getSeats().stream().map(this::seatItem).toList(),
                price(b.effectiveDailyPrice()), price(b.effectiveMonthlyPrice()));
    }

    public LegacyResponses.SeatResponse seat(Seat s) {
        return new LegacyResponses.SeatResponse(s.getId(), s.getSeatNumber(), block(s.getBlock()),
                price(s.getPricePerDay()), s.getStatus() == SeatStatus.RESERVED);
    }

    public LegacyResponses.AmenityResponse amenity(Block b) {
        Set<String> codes = b.getAmenities().stream().map(Amenity::getCode).collect(Collectors.toSet());
        return new LegacyResponses.AmenityResponse(b.getId(), codes.contains(AC), codes.contains(WIFI),
                codes.contains(WATER), codes.contains(LOCKER), codes.contains(NEWSPAPER), block(b));
    }

    public LegacyResponses.ImageResponse image(StudyHallImage i) {
        return new LegacyResponses.ImageResponse(i.getId(), i.getUrl(), property(i.getStudyHall()));
    }

    public static boolean hasLegacyAmenities(Block b) {
        return b.getAmenities().stream().anyMatch(a -> LEGACY_AMENITY_CODES.contains(a.getCode()));
    }

    /** Legacy "lat,lng" string ↔ latitude / longitude. Invalid input yields nulls. */
    public static Double[] parseCoordinates(String googleCoordinates) {
        if (googleCoordinates == null || !googleCoordinates.contains(",")) {
            return new Double[]{null, null};
        }
        try {
            String[] parts = googleCoordinates.split(",");
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                return new Double[]{null, null};
            }
            return new Double[]{lat, lng};
        } catch (RuntimeException ex) {
            return new Double[]{null, null};
        }
    }

    /** Legacy prices are non-null doubles with 0 meaning "not set". */
    public static BigDecimal toPrice(Double legacyPrice) {
        return legacyPrice == null || legacyPrice <= 0 ? null : BigDecimal.valueOf(legacyPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private LegacyResponses.PropertyItem propertyItem(StudyHall h) {
        return new LegacyResponses.PropertyItem(h.getId(), h.getName(), h.getAddressLine(), coordinates(h),
                h.getImages().stream().map(this::imageItem).toList(),
                h.getBlocks().stream().map(this::blockItem).toList());
    }

    private LegacyResponses.BlockItem blockItem(Block b) {
        return new LegacyResponses.BlockItem(b.getId(), b.getName(), b.getSeats().stream().map(this::seatItem).toList(),
                price(b.effectiveDailyPrice()), price(b.effectiveMonthlyPrice()));
    }

    private LegacyResponses.SeatItem seatItem(Seat s) {
        return new LegacyResponses.SeatItem(s.getId(), s.getSeatNumber(), price(s.getPricePerDay()),
                s.getStatus() == SeatStatus.RESERVED);
    }

    private LegacyResponses.ImageItem imageItem(StudyHallImage i) {
        return new LegacyResponses.ImageItem(i.getId(), i.getUrl());
    }

    private static String ownerName(VendorProfile v) {
        return v.getContactName() != null ? v.getContactName() : v.getBusinessName();
    }

    private static String coordinates(StudyHall h) {
        return h.getLatitude() == null || h.getLongitude() == null ? null : h.getLatitude() + "," + h.getLongitude();
    }

    private static double price(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }
}
