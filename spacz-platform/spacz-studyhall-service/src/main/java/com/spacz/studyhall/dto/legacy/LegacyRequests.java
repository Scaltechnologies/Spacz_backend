package com.spacz.studyhall.dto.legacy;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;

/**
 * Request bodies of the legacy Partner API, field for field.
 */
public final class LegacyRequests {

    private LegacyRequests() {
    }

    public record OwnerRequest(@Size(max = 120) String ownerName, @Size(max = 254) String ownerEmail,
                               @Size(max = 20) String ownerPhoneNumber, @Size(max = 255) String address,
                               LegacyRefs.LoginRef userLogin) {
    }

    public record PropertyRequest(@Size(max = 150) String propertyName, @Size(max = 255) String address,
                                  @Size(max = 255) String googleCoordinates, LegacyRefs.OwnerRef owner) {

        public Long ownerId() {
            return owner == null ? null : owner.ownerId();
        }
    }

    public record ImageRequest(@Size(max = 500) String imageUrl, LegacyRefs.PropertyRef property) {

        public Long propertyId() {
            return property == null ? null : property.propertyId();
        }
    }

    public record BlockRequest(@Size(max = 80) String blockName, LegacyRefs.PropertyRef property,
                               Double blockDailyPrice, Double blockMonthlyPrice) {

        public Long propertyId() {
            return property == null ? null : property.propertyId();
        }
    }

    public record AmenityRequest(Boolean ac, Boolean wifi, Boolean water, Boolean lockers, Boolean newspapers,
                                 LegacyRefs.BlockRef block) {

        public Long blockId() {
            return block == null ? null : block.blockId();
        }
    }

    public record SeatRequest(@Size(max = 20) String seatNumber, LegacyRefs.BlockRef block,
                              @JsonAlias("isReserved") Boolean reserved, Double seatPrice) {

        public Long blockId() {
            return block == null ? null : block.blockId();
        }
    }
}
