package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.audit.AuditEvents;
import com.spacz.studyhall.config.StudyHallProperties;
import com.spacz.studyhall.dto.legacy.LegacyRequests;
import com.spacz.studyhall.dto.legacy.LegacyResponses;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.SeatType;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ConflictException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.exception.SpaczException;
import com.spacz.studyhall.mapper.LegacyMapper;
import com.spacz.studyhall.repository.AmenityRepository;
import com.spacz.studyhall.repository.BlockRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.EnrollmentRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.repository.VendorProfileRepository;
import com.spacz.studyhall.service.LegacyPartnerService;
import com.spacz.studyhall.service.support.DefaultHours;
import com.spacz.studyhall.service.support.SeatNumbering;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The legacy Partner API on top of the new model. Semantics follow spacz-partner-service (status
 * codes, PUT replacing fields, unknown IDs ignored on DELETE), with two deliberate differences:
 * every call is scoped to the calling vendor (the legacy API had no authentication), and seat
 * numbers must be unique within a study hall.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class LegacyPartnerServiceImpl implements LegacyPartnerService {

    private static final String UNTITLED_HALL = "Untitled study hall";

    private final VendorProfileRepository vendorRepository;
    private final StudyHallRepository studyHallRepository;
    private final BlockRepository blockRepository;
    private final SeatRepository seatRepository;
    private final AmenityRepository amenityRepository;
    private final BookingRepository bookingRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final LegacyMapper mapper;
    private final StudyHallProperties properties;
    private final AuditEvents auditEvents;
    private final Clock clock;

    // ------------------------------------------------------------------ owners

    /**
     * One owner per account: POST fills in (and submits) the profile created at registration, or
     * creates it. A second POST once the owner is submitted is a conflict.
     */
    @Override
    public LegacyResponses.OwnerResponse createOwner(Long vendorId, LegacyRequests.OwnerRequest request) {
        VendorProfile vendor = vendorRepository.findByVendorId(vendorId).orElseGet(() -> VendorProfile.draft(vendorId));
        if (vendor.getId() != null && vendor.getSubmittedAt() != null) {
            throw new ConflictException("CONFLICT", "An owner already exists for this account; update it with PUT /api/owners/"
                    + vendor.getId());
        }
        VendorAccess.ensureWritable(vendor);
        applyOwner(vendor, request);
        vendor.submitWithoutCompletenessCheck(clock.instant());
        VendorProfile saved = vendorRepository.saveAndFlush(vendor);
        auditEvents.record(vendorId, "VENDOR", "VENDOR_SUBMITTED", "VENDOR", vendorId,
                "Vendor " + saved.displayName() + " registered through the Partner app");
        return mapper.ownerWithProperties(saved, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.OwnerResponse> owners(Long vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .map(v -> List.of(mapper.ownerWithProperties(v, halls(v))))
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.OwnerResponse owner(Long vendorId, Long ownerId) {
        VendorProfile vendor = ownOwner(vendorId, ownerId);
        return mapper.ownerWithProperties(vendor, halls(vendor));
    }

    @Override
    public LegacyResponses.OwnerResponse updateOwner(Long vendorId, Long ownerId, LegacyRequests.OwnerRequest request) {
        VendorProfile vendor = ownOwner(vendorId, ownerId);
        VendorAccess.ensureWritable(vendor);
        applyOwner(vendor, request);
        return mapper.ownerWithProperties(vendorRepository.saveAndFlush(vendor), halls(vendor));
    }

    @Override
    public void deleteOwner(Long vendorId, Long ownerId) {
        vendorRepository.findByVendorId(vendorId).filter(v -> v.getId().equals(ownerId)).ifPresent(vendor -> {
            if (bookingRepository.existsForVendor(vendor.getId())) {
                throw new ConflictException("CONFLICT", "This owner has bookings and cannot be deleted");
            }
            halls(vendor).forEach(studyHallRepository::delete);
            vendorRepository.delete(vendor);
            vendorRepository.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.PropertyResponse> ownerProperties(Long vendorId, Long ownerId) {
        return halls(ownOwner(vendorId, ownerId)).stream().map(mapper::propertyWithChildren).toList();
    }

    // -------------------------------------------------------------- properties

    /** The legacy app has no submit step: a new property is submitted for approval straight away. */
    @Override
    public LegacyResponses.PropertyResponse createProperty(Long vendorId, LegacyRequests.PropertyRequest request) {
        if (request.ownerId() == null) {
            throw badRequest("owner.ownerId is required");
        }
        VendorProfile vendor = ownOwner(vendorId, request.ownerId());
        VendorAccess.ensureWritable(vendor);
        StudyHall hall = StudyHall.draft(vendor);
        hall.setName(request.propertyName() == null || request.propertyName().isBlank()
                ? UNTITLED_HALL : request.propertyName().trim());
        hall.setAddressLine(trimToNull(request.address()));
        hall.setCity(vendor.getCity());
        applyCoordinates(hall, request.googleCoordinates());
        DefaultHours.seed(hall, DefaultHours.OPEN, DefaultHours.CLOSE);
        hall.submitForApproval(properties.approvalRequired(), clock.instant());
        StudyHall saved = studyHallRepository.saveAndFlush(hall);
        auditEvents.record(vendorId, "VENDOR", "STUDY_HALL_CREATED", "STUDY_HALL", saved.getId(),
                "Created study hall '" + saved.getName() + "' through the Partner app");
        return mapper.propertyWithChildren(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.PropertyResponse> properties(Long vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .map(v -> halls(v).stream().map(mapper::propertyWithChildren).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.PropertyResponse property(Long vendorId, Long propertyId) {
        return mapper.propertyWithChildren(ownHall(vendorId, propertyId));
    }

    @Override
    public LegacyResponses.PropertyResponse updateProperty(Long vendorId, Long propertyId,
                                                           LegacyRequests.PropertyRequest request) {
        StudyHall hall = ownHall(vendorId, propertyId);
        VendorAccess.ensureWritable(hall.getVendor());
        if (request.ownerId() != null) {
            ownOwner(vendorId, request.ownerId());
        }
        if (request.propertyName() != null && !request.propertyName().isBlank()) {
            hall.setName(request.propertyName().trim());
        }
        if (request.address() != null) {
            hall.setAddressLine(trimToNull(request.address()));
        }
        if (request.googleCoordinates() != null) {
            applyCoordinates(hall, request.googleCoordinates());
        }
        return mapper.propertyWithChildren(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    public void deleteProperty(Long vendorId, Long propertyId) {
        studyHallRepository.findOwned(propertyId, vendorId).ifPresent(hall -> {
            if (bookingRepository.existsByStudyHallId(propertyId)) {
                throw new ConflictException("CONFLICT", "This property has bookings and cannot be deleted");
            }
            studyHallRepository.delete(hall);
            studyHallRepository.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.BlockResponse> propertyBlocks(Long vendorId, Long propertyId) {
        return ownHall(vendorId, propertyId).getBlocks().stream().map(mapper::blockWithSeats).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.ImageResponse> propertyImages(Long vendorId, Long propertyId) {
        return ownHall(vendorId, propertyId).getImages().stream().map(mapper::image).toList();
    }

    // ------------------------------------------------------------------ blocks

    @Override
    public LegacyResponses.BlockResponse createBlock(Long vendorId, LegacyRequests.BlockRequest request) {
        if (request.propertyId() == null) {
            throw badRequest("property.propertyId is required");
        }
        StudyHall hall = ownHall(vendorId, request.propertyId());
        VendorAccess.ensureWritable(hall.getVendor());
        Block block = Block.of(hall, request.blockName() == null || request.blockName().isBlank()
                ? "Block" : request.blockName().trim(), 1, 10, blockRepository.maxDisplayOrder(hall.getId()) + 1);
        block.setDailyPrice(LegacyMapper.toPrice(request.blockDailyPrice()));
        block.setMonthlyPrice(LegacyMapper.toPrice(request.blockMonthlyPrice()));
        hall.getBlocks().add(block);
        return mapper.blockWithSeats(blockRepository.saveAndFlush(block));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.BlockResponse> blocks(Long vendorId) {
        return blockRepository.findAllOwned(vendorId).stream().map(mapper::blockWithSeats).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.BlockResponse block(Long vendorId, Long blockId) {
        return mapper.blockWithSeats(ownBlock(vendorId, blockId));
    }

    @Override
    public LegacyResponses.BlockResponse updateBlock(Long vendorId, Long blockId, LegacyRequests.BlockRequest request) {
        Block block = ownBlock(vendorId, blockId);
        VendorAccess.ensureWritable(block.getStudyHall().getVendor());
        if (request.propertyId() != null && !request.propertyId().equals(block.getStudyHall().getId())) {
            StudyHall target = ownHall(vendorId, request.propertyId());
            if (bookingRepository.existsBySeat_Block_Id(blockId)) {
                throw new ConflictException("CONFLICT", "A block with bookings cannot be moved to another property");
            }
            block.getStudyHall().getBlocks().remove(block);
            block.setStudyHall(target);
            target.getBlocks().add(block);
            Set<String> taken = seatRepository.findSeatNumbersLowercase(target.getId());
            block.getSeats().forEach(seat -> {
                seat.moveTo(block, seat.getRowIndex(), seat.getColumnIndex());
                seat.setSeatNumber(SeatNumbering.unique(seat.getSeatNumber(), taken));
            });
        }
        if (request.blockName() != null && !request.blockName().isBlank()) {
            block.setName(request.blockName().trim());
        }
        if (request.blockDailyPrice() != null) {
            block.setDailyPrice(LegacyMapper.toPrice(request.blockDailyPrice()));
        }
        if (request.blockMonthlyPrice() != null) {
            block.setMonthlyPrice(LegacyMapper.toPrice(request.blockMonthlyPrice()));
        }
        return mapper.blockWithSeats(blockRepository.saveAndFlush(block));
    }

    @Override
    public void deleteBlock(Long vendorId, Long blockId) {
        blockRepository.findOwned(blockId, vendorId).ifPresent(block -> {
            if (bookingRepository.existsBySeat_Block_Id(blockId)
                    || block.getSeats().stream().anyMatch(s -> enrollmentRepository.existsBySeat_Id(s.getId()))) {
                throw new ConflictException("CONFLICT", "This block has bookings and cannot be deleted");
            }
            block.getStudyHall().getBlocks().remove(block);
            blockRepository.delete(block);
            blockRepository.flush();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.SeatResponse> blockSeats(Long vendorId, Long blockId) {
        return ownBlock(vendorId, blockId).getSeats().stream().map(mapper::seat).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.AmenityResponse blockAmenity(Long vendorId, Long blockId) {
        Block block = ownBlock(vendorId, blockId);
        if (!LegacyMapper.hasLegacyAmenities(block)) {
            throw new ResourceNotFoundException("Amenity of block", blockId);
        }
        return mapper.amenity(block);
    }

    // ------------------------------------------------------------------- seats

    /** Legacy seats have no position: they are placed in the next free cell of the block. */
    @Override
    public LegacyResponses.SeatResponse createSeat(Long vendorId, LegacyRequests.SeatRequest request) {
        if (request.blockId() == null) {
            throw badRequest("block.blockId is required");
        }
        Block block = ownBlock(vendorId, request.blockId());
        VendorAccess.ensureWritable(block.getStudyHall().getVendor());
        int[] cell = freeCell(block);
        String number = seatNumber(request.seatNumber(), block, null);
        Seat seat = Seat.of(block, number, cell[0], cell[1], SeatType.STANDARD);
        seat.setStatus(Boolean.TRUE.equals(request.reserved()) ? SeatStatus.RESERVED : SeatStatus.AVAILABLE);
        seat.setPricePerDay(LegacyMapper.toPrice(request.seatPrice()));
        block.getSeats().add(seat);
        return mapper.seat(seatRepository.saveAndFlush(seat));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.SeatResponse> seats(Long vendorId) {
        return seatRepository.findAllOwned(vendorId).stream().map(mapper::seat).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.SeatResponse seat(Long vendorId, Long seatId) {
        return mapper.seat(ownSeat(vendorId, seatId));
    }

    /** Like the legacy API: number and reserved flag are replaced; block and price change only when sent. */
    @Override
    public LegacyResponses.SeatResponse updateSeat(Long vendorId, Long seatId, LegacyRequests.SeatRequest request) {
        Seat seat = ownSeat(vendorId, seatId);
        VendorAccess.ensureWritable(seat.getStudyHall().getVendor());
        if (request.blockId() != null && !request.blockId().equals(seat.getBlock().getId())) {
            Block target = ownBlock(vendorId, request.blockId());
            if (bookingRepository.existsBySeat_Id(seatId) || enrollmentRepository.existsBySeat_Id(seatId)) {
                throw new ConflictException("CONFLICT", "A seat with bookings cannot be moved to another block");
            }
            int[] cell = freeCell(target);
            seat.getBlock().getSeats().remove(seat);
            seat.moveTo(target, cell[0], cell[1]);
            target.getSeats().add(seat);
        }
        if (request.seatNumber() != null && !request.seatNumber().isBlank()) {
            seat.setSeatNumber(seatNumber(request.seatNumber(), seat.getBlock(), seat.getId()));
        }
        boolean reserved = Boolean.TRUE.equals(request.reserved());
        if (reserved) {
            seat.setStatus(SeatStatus.RESERVED);
        } else if (seat.getStatus() == SeatStatus.RESERVED) {
            seat.setStatus(SeatStatus.AVAILABLE);
        }
        if (request.seatPrice() != null) {
            seat.setPricePerDay(LegacyMapper.toPrice(request.seatPrice()));
        }
        return mapper.seat(seatRepository.saveAndFlush(seat));
    }

    @Override
    public void deleteSeat(Long vendorId, Long seatId) {
        seatRepository.findOwned(seatId, vendorId).ifPresent(seat -> {
            if (bookingRepository.existsBySeat_Id(seatId) || enrollmentRepository.existsBySeat_Id(seatId)) {
                throw new ConflictException("CONFLICT", "This seat has bookings and cannot be deleted");
            }
            seat.getBlock().getSeats().remove(seat);
            seatRepository.delete(seat);
            seatRepository.flush();
        });
    }

    // --------------------------------------------------------------- amenities

    /** The legacy "amenity record" of a block = its AC / WIFI / WATER / LOCKER / NEWSPAPER catalog amenities. */
    @Override
    public LegacyResponses.AmenityResponse createAmenity(Long vendorId, LegacyRequests.AmenityRequest request) {
        if (request.blockId() == null) {
            throw badRequest("block.blockId is required");
        }
        Block block = ownBlock(vendorId, request.blockId());
        VendorAccess.ensureWritable(block.getStudyHall().getVendor());
        if (LegacyMapper.hasLegacyAmenities(block)) {
            throw new ConflictException("CONFLICT", "Block " + block.getId() + " already has an amenity record");
        }
        applyAmenityFlags(block, request);
        return mapper.amenity(blockRepository.saveAndFlush(block));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.AmenityResponse> amenities(Long vendorId) {
        return blockRepository.findAllOwned(vendorId).stream()
                .filter(LegacyMapper::hasLegacyAmenities)
                .map(mapper::amenity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.AmenityResponse amenity(Long vendorId, Long amenityId) {
        return blockAmenity(vendorId, amenityId);
    }

    @Override
    public LegacyResponses.AmenityResponse updateAmenity(Long vendorId, Long amenityId,
                                                         LegacyRequests.AmenityRequest request) {
        Block block = ownBlock(vendorId, amenityId);
        if (!LegacyMapper.hasLegacyAmenities(block)) {
            throw new ResourceNotFoundException("Amenity", amenityId);
        }
        VendorAccess.ensureWritable(block.getStudyHall().getVendor());
        if (request.blockId() != null && !request.blockId().equals(block.getId())) {
            Block target = ownBlock(vendorId, request.blockId());
            if (LegacyMapper.hasLegacyAmenities(target)) {
                throw new ConflictException("CONFLICT", "Block " + target.getId() + " already has an amenity record");
            }
            clearAmenityFlags(block);
            applyAmenityFlags(target, request);
            return mapper.amenity(blockRepository.saveAndFlush(target));
        }
        applyAmenityFlags(block, request);
        return mapper.amenity(blockRepository.saveAndFlush(block));
    }

    @Override
    public void deleteAmenity(Long vendorId, Long amenityId) {
        blockRepository.findOwned(amenityId, vendorId).ifPresent(block -> {
            clearAmenityFlags(block);
            blockRepository.flush();
        });
    }

    // ------------------------------------------------------------------ images

    @Override
    public LegacyResponses.ImageResponse createImage(Long vendorId, LegacyRequests.ImageRequest request) {
        if (request.propertyId() == null) {
            throw badRequest("property.propertyId is required");
        }
        if (request.imageUrl() == null || request.imageUrl().isBlank()) {
            throw badRequest("imageUrl is required");
        }
        StudyHall hall = ownHall(vendorId, request.propertyId());
        VendorAccess.ensureWritable(hall.getVendor());
        StudyHallImage image = StudyHallImage.of(hall, request.imageUrl().trim(), null, hall.getImages().size(),
                hall.getImages().isEmpty());
        hall.getImages().add(image);
        // The hall is managed: flushing cascades PERSIST to this very instance, so it gets its ID.
        studyHallRepository.flush();
        return mapper.image(image);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyResponses.ImageResponse> images(Long vendorId) {
        return vendorRepository.findByVendorId(vendorId)
                .map(v -> halls(v).stream().flatMap(h -> h.getImages().stream()).map(mapper::image).toList())
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyResponses.ImageResponse image(Long vendorId, Long imageId) {
        return mapper.image(ownImage(vendorId, imageId));
    }

    @Override
    public LegacyResponses.ImageResponse updateImage(Long vendorId, Long imageId, LegacyRequests.ImageRequest request) {
        StudyHallImage image = ownImage(vendorId, imageId);
        VendorAccess.ensureWritable(image.getStudyHall().getVendor());
        if (request.propertyId() != null && !request.propertyId().equals(image.getStudyHall().getId())) {
            StudyHall target = ownHall(vendorId, request.propertyId());
            image.getStudyHall().getImages().remove(image);
            image.setStudyHall(target);
            image.setCover(target.getImages().isEmpty());
            target.getImages().add(image);
        }
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            image.setUrl(request.imageUrl().trim());
        }
        studyHallRepository.flush();
        return mapper.image(image);
    }

    @Override
    public void deleteImage(Long vendorId, Long imageId) {
        findImage(vendorId, imageId).ifPresent(image -> {
            StudyHall hall = image.getStudyHall();
            hall.getImages().remove(image);
            if (image.isCover() && !hall.getImages().isEmpty()) {
                hall.getImages().get(0).setCover(true);
            }
            studyHallRepository.flush();
        });
    }

    // ----------------------------------------------------------------- helpers

    private VendorProfile ownOwner(Long vendorId, Long ownerId) {
        return vendorRepository.findByVendorId(vendorId)
                .filter(v -> v.getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Owner", ownerId));
    }

    private List<StudyHall> halls(VendorProfile vendor) {
        return vendor.getId() == null ? List.of() : studyHallRepository.findByVendor_IdOrderByIdAsc(vendor.getId());
    }

    private StudyHall ownHall(Long vendorId, Long propertyId) {
        return studyHallRepository.findOwned(propertyId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", propertyId));
    }

    private Block ownBlock(Long vendorId, Long blockId) {
        return blockRepository.findOwned(blockId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Block", blockId));
    }

    private Seat ownSeat(Long vendorId, Long seatId) {
        return seatRepository.findOwned(seatId, vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Seat", seatId));
    }

    private Optional<StudyHallImage> findImage(Long vendorId, Long imageId) {
        return vendorRepository.findByVendorId(vendorId).flatMap(v -> halls(v).stream()
                .flatMap(h -> h.getImages().stream())
                .filter(i -> i.getId().equals(imageId))
                .findFirst());
    }

    private StudyHallImage ownImage(Long vendorId, Long imageId) {
        return findImage(vendorId, imageId).orElseThrow(() -> new ResourceNotFoundException("Image", imageId));
    }

    private static int[] freeCell(Block block) {
        return block.nextFreeCell().orElseThrow(() ->
                new BusinessRuleException("Block " + block.getName() + " is full (" + Block.MAX_GRID + " rows)"));
    }

    /** Seat numbers are unique per hall; the legacy app may omit them (a number is generated then). */
    private String seatNumber(String requested, Block block, Long seatId) {
        String number = requested == null || requested.isBlank()
                ? "S" + (block.getStudyHall().getBlocks().stream().mapToInt(b -> b.getSeats().size()).sum() + 1)
                : requested.trim();
        boolean taken = seatId == null
                ? seatRepository.existsByStudyHallIdAndSeatNumberIgnoreCase(block.getStudyHall().getId(), number)
                : seatRepository.existsByStudyHallIdAndSeatNumberIgnoreCaseAndIdNot(block.getStudyHall().getId(), number, seatId);
        if (taken) {
            if (requested == null || requested.isBlank()) {
                return SeatNumbering.unique(number, seatRepository.findSeatNumbersLowercase(block.getStudyHall().getId()));
            }
            throw new ConflictException("CONFLICT", "Seat number " + number + " already exists in this property");
        }
        return number;
    }

    private void applyOwner(VendorProfile vendor, LegacyRequests.OwnerRequest request) {
        String name = trimToNull(request.ownerName());
        vendor.setContactName(name);
        if (vendor.getBusinessName() == null) {
            vendor.setBusinessName(name);
        }
        vendor.setEmail(trimToNull(request.ownerEmail()));
        vendor.setPhone(trimToNull(request.ownerPhoneNumber()));
        vendor.setAddressLine(trimToNull(request.address()));
    }

    private static void applyCoordinates(StudyHall hall, String googleCoordinates) {
        Double[] coordinates = LegacyMapper.parseCoordinates(googleCoordinates);
        hall.setLatitude(coordinates[0]);
        hall.setLongitude(coordinates[1]);
    }

    private void applyAmenityFlags(Block block, LegacyRequests.AmenityRequest request) {
        clearAmenityFlags(block);
        Map<String, Amenity> catalog = amenityRepository.findByCodeIn(LegacyMapper.LEGACY_AMENITY_CODES).stream()
                .collect(Collectors.toMap(Amenity::getCode, Function.identity()));
        List<String> wanted = new ArrayList<>();
        if (Boolean.TRUE.equals(request.ac())) {
            wanted.add(LegacyMapper.AC);
        }
        if (Boolean.TRUE.equals(request.wifi())) {
            wanted.add(LegacyMapper.WIFI);
        }
        if (Boolean.TRUE.equals(request.water())) {
            wanted.add(LegacyMapper.WATER);
        }
        if (Boolean.TRUE.equals(request.lockers())) {
            wanted.add(LegacyMapper.LOCKER);
        }
        if (Boolean.TRUE.equals(request.newspapers())) {
            wanted.add(LegacyMapper.NEWSPAPER);
        }
        wanted.stream().map(catalog::get).filter(java.util.Objects::nonNull).forEach(block.getAmenities()::add);
    }

    private static void clearAmenityFlags(Block block) {
        block.getAmenities().removeIf(a -> LegacyMapper.LEGACY_AMENITY_CODES.contains(a.getCode().toUpperCase(Locale.ROOT)));
    }

    private static SpaczException badRequest(String message) {
        return new SpaczException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
