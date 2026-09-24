package com.spacz.studyhall.dto.legacy;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reference objects of the legacy Partner API ({ "ownerId": 1 } etc.).
 */
public final class LegacyRefs {

    private LegacyRefs() {
    }

    public record OwnerRef(@Schema(example = "1") Long ownerId) {
    }

    public record PropertyRef(@Schema(example = "1") Long propertyId) {
    }

    public record BlockRef(@Schema(example = "1") Long blockId) {
    }

    /** The owner's login. {@code loginId} is now the SPACZ auth account ID. */
    public record LoginRef(@Schema(example = "1") Long loginId) {

        public static LoginRef of(Long loginId) {
            return loginId == null ? null : new LoginRef(loginId);
        }
    }
}
