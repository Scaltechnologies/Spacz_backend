package com.spacz.studyhall.dto.internal;

import java.util.List;
import java.util.Map;

/**
 * Legacy ID → new ID for every imported record.
 *
 * @param created false when the owner had already been imported (nothing changed)
 */
public record ImportVendorResult(Long legacyOwnerId, Long vendorProfileId, boolean created,
                                 Map<Long, Long> properties, Map<Long, Long> blocks, Map<Long, Long> seats,
                                 Map<Long, Long> images, List<String> warnings) {
}
