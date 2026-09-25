package com.spacz.studyhall.service;

import com.spacz.studyhall.dto.internal.ImportBookingRequest;
import com.spacz.studyhall.dto.internal.ImportBookingResult;
import com.spacz.studyhall.dto.internal.ImportVendorRequest;
import com.spacz.studyhall.dto.internal.ImportVendorResult;

/**
 * One-time migration of the legacy MySQL `spacz` data (idempotent on legacy IDs).
 */
public interface LegacyImportService {

    ImportVendorResult importVendor(ImportVendorRequest request);

    ImportBookingResult importBooking(ImportBookingRequest request);
}
