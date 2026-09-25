package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.dto.internal.ImportBookingRequest;
import com.spacz.studyhall.dto.internal.ImportBookingResult;
import com.spacz.studyhall.dto.internal.ImportVendorRequest;
import com.spacz.studyhall.dto.internal.ImportVendorResult;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.mapper.LegacyMapper;
import com.spacz.studyhall.repository.AmenityRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.service.EnrollmentService;
import com.spacz.studyhall.service.LegacyImportService;
import com.spacz.studyhall.service.support.DefaultHours;
import com.spacz.studyhall.service.support.SeatNumbering;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Imports legacy owners (with properties, images, blocks, amenity flags and seats) and bookings.
 * Imported vendors are APPROVED and their halls ACTIVE: they were already live in the old system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyImportServiceImpl implements LegacyImportService {

    private final VendorProfileRepository vendorRepository;
    private final StudyHallRepository studyHallRepository;
    private final SeatRepository seatRepository;
    private final AmenityRepository amenityRepository;
    private final BookingRepository bookingRepository;
    private final EnrollmentService enrollmentService;
    private final Clock clock;

    @Override
    @Transactional
    public ImportVendorResult importVendor(ImportVendorRequest request) {
        VendorProfile existing = vendorRepository.findByLegacyOwnerId(request.legacyOwnerId()).orElse(null);
        if (existing != null) {
            return mapping(existing, false, List.of());
        }
        Instant now = clock.instant();
        List<String> warnings = new ArrayList<>();
        VendorProfile vendor = vendorRepository.findByVendorId(request.vendorId())
                .orElseGet(() -> VendorProfile.draft(request.vendorId()));
        vendor.setContactName(trimToNull(request.ownerName()));
        vendor.setBusinessName(vendor.getBusinessName() != null ? vendor.getBusinessName() : trimToNull(request.ownerName()));
        vendor.setEmail(trimToNull(request.ownerEmail()));
        vendor.setPhone(trimToNull(request.ownerPhoneNumber()));
        vendor.setAddressLine(trimToNull(request.address()));
        vendor.markImported(request.legacyOwnerId(), now);
        vendorRepository.saveAndFlush(vendor);

        Map<String, Amenity> catalog = amenityRepository.findByCodeIn(LegacyMapper.LEGACY_AMENITY_CODES).stream()
                .collect(Collectors.toMap(Amenity::getCode, Function.identity()));
        for (ImportVendorRequest.Property source : nullSafe(request.properties())) {
            StudyHall hall = StudyHall.draft(vendor);
            hall.setName(blank(source.propertyName()) ? "Study hall " + source.legacyPropertyId() : source.propertyName().trim());
            hall.setAddressLine(trimToNull(source.address()));
            Double[] coordinates = LegacyMapper.parseCoordinates(source.googleCoordinates());
            if (source.googleCoordinates() != null && coordinates[0] == null) {
                warnings.add("property " + source.legacyPropertyId() + ": unreadable coordinates '" + source.googleCoordinates() + "'");
            }
            hall.setLatitude(coordinates[0]);
            hall.setLongitude(coordinates[1]);
            DefaultHours.seed(hall, DefaultHours.OPEN, DefaultHours.CLOSE);
            hall.markImported(source.legacyPropertyId(), now);

            int order = 0;
            for (ImportVendorRequest.Image image : nullSafe(source.images())) {
                if (blank(image.imageUrl())) {
                    warnings.add("image " + image.legacyImageId() + ": empty URL, skipped");
                    continue;
                }
                StudyHallImage copy = StudyHallImage.of(hall, image.imageUrl().trim(), null, order, order == 0);
                copy.setLegacyImageId(image.legacyImageId());
                hall.getImages().add(copy);
                order++;
            }

            Set<String> seatNumbers = new HashSet<>();
            int blockOrder = 1;
            for (ImportVendorRequest.Block source2 : nullSafe(source.blocks())) {
                Block block = Block.of(hall, blank(source2.blockName()) ? "Block " + source2.legacyBlockId()
                        : source2.blockName().trim(), 1, 10, blockOrder++);
                block.setLegacyBlockId(source2.legacyBlockId());
                block.setDailyPrice(LegacyMapper.toPrice(source2.blockDailyPrice()));
                block.setMonthlyPrice(LegacyMapper.toPrice(source2.blockMonthlyPrice()));
                if (source2.amenity() != null) {
                    addFlag(block, catalog, LegacyMapper.AC, source2.amenity().ac());
                    addFlag(block, catalog, LegacyMapper.WIFI, source2.amenity().wifi());
                    addFlag(block, catalog, LegacyMapper.WATER, source2.amenity().water());
                    addFlag(block, catalog, LegacyMapper.LOCKER, source2.amenity().lockers());
                    addFlag(block, catalog, LegacyMapper.NEWSPAPER, source2.amenity().newspapers());
                }
                for (ImportVendorRequest.Seat seatSource : nullSafe(source2.seats())) {
                    int[] cell = block.nextFreeCell().orElse(null);
                    if (cell == null) {
                        warnings.add("seat " + seatSource.legacySeatId() + ": block " + source2.legacyBlockId() + " is full, skipped");
                        continue;
                    }
                    String wanted = blank(seatSource.seatNumber()) ? "S" + seatSource.legacySeatId() : seatSource.seatNumber().trim();
                    String number = SeatNumbering.unique(wanted, seatNumbers);
                    if (!number.equals(wanted)) {
                        warnings.add("seat " + seatSource.legacySeatId() + ": duplicate number '" + wanted + "' renamed to " + number);
                    }
                    Seat seat = Seat.of(block, number, cell[0], cell[1], SeatType.STANDARD);
                    seat.setLegacySeatId(seatSource.legacySeatId());
                    seat.setStatus(seatSource.reserved() ? SeatStatus.RESERVED : SeatStatus.AVAILABLE);
                    seat.setPricePerDay(LegacyMapper.toPrice(seatSource.seatPrice()));
                    block.getSeats().add(seat);
                }
                if (block.effectiveDailyPrice() == null && block.getSeats().stream().allMatch(s -> s.getPricePerDay() == null)) {
                    warnings.add("block " + source2.legacyBlockId() + ": no price set; its seats cannot be booked until a price is added");
                }
                hall.getBlocks().add(block);
            }
            studyHallRepository.save(hall);
        }
        studyHallRepository.flush();
        log.info("Imported legacy owner {} as vendor {} ({} warnings)", request.legacyOwnerId(), request.vendorId(), warnings.size());
        return mapping(vendor, true, warnings);
    }

    @Override
    @Transactional
    public ImportBookingResult importBooking(ImportBookingRequest request) {
        var existing = bookingRepository.findByLegacyBookingId(request.legacyBookingId());
        if (existing.isPresent()) {
            return new ImportBookingResult(request.legacyBookingId(), existing.get().getId(), "ALREADY_IMPORTED");
        }
        Seat seat = seatRepository.findByLegacySeatId(request.legacySeatId()).orElse(null);
        if (seat == null) {
            return new ImportBookingResult(request.legacyBookingId(), null, "SKIPPED_SEAT_UNKNOWN");
        }
        if (bookingRepository.existsOverlapping(seat.getId(), request.startDate(), request.endDate(), BookingStatus.OCCUPYING)) {
            return new ImportBookingResult(request.legacyBookingId(), null, "SKIPPED_OVERLAP");
        }
        BigDecimal price = seat.effectiveDailyPrice() != null ? seat.effectiveDailyPrice() : BigDecimal.ZERO;
        Booking booking = bookingRepository.saveAndFlush(Booking.imported(BookingServiceImpl.newReference(),
                request.legacyBookingId(), request.userId(), seat, request.startDate(), request.endDate(), price,
                clock.instant()));
        enrollmentService.recordConfirmedBooking(booking);
        return new ImportBookingResult(request.legacyBookingId(), booking.getId(), "CREATED");
    }

    private ImportVendorResult mapping(VendorProfile vendor, boolean created, List<String> warnings) {
        Map<Long, Long> properties = new LinkedHashMap<>();
        Map<Long, Long> blocks = new LinkedHashMap<>();
        Map<Long, Long> seats = new LinkedHashMap<>();
        Map<Long, Long> images = new LinkedHashMap<>();
        for (StudyHall hall : studyHallRepository.findByVendor_IdOrderByIdAsc(vendor.getId())) {
            put(properties, hall.getLegacyPropertyId(), hall.getId());
            hall.getImages().forEach(i -> put(images, i.getLegacyImageId(), i.getId()));
            hall.getBlocks().forEach(b -> {
                put(blocks, b.getLegacyBlockId(), b.getId());
                b.getSeats().forEach(s -> put(seats, s.getLegacySeatId(), s.getId()));
            });
        }
        return new ImportVendorResult(vendor.getLegacyOwnerId(), vendor.getId(), created, properties, blocks, seats,
                images, warnings);
    }

    private static void put(Map<Long, Long> map, Long legacyId, Long newId) {
        if (legacyId != null) {
            map.put(legacyId, newId);
        }
    }

    private static void addFlag(Block block, Map<String, Amenity> catalog, String code, boolean enabled) {
        if (enabled && catalog.containsKey(code)) {
            block.getAmenities().add(catalog.get(code));
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }
}
