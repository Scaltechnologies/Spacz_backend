package com.spacz.studyhall.service.impl;

import com.spacz.studyhall.audit.AuditEvents;
import com.spacz.studyhall.config.StudyHallProperties;
import com.spacz.studyhall.dto.PageResponse;
import com.spacz.studyhall.dto.hall.CreateStudyHallRequest;
import com.spacz.studyhall.dto.hall.ImageRequest;
import com.spacz.studyhall.dto.hall.OperatingHoursRequest;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.hall.UpdateStudyHallRequest;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.entity.OperatingHours;
import com.spacz.studyhall.entity.Program;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatStatus;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.entity.VendorStatus;
import com.spacz.studyhall.exception.BusinessRuleException;
import com.spacz.studyhall.exception.ResourceNotFoundException;
import com.spacz.studyhall.repository.AmenityRepository;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.repository.ProgramRepository;
import com.spacz.studyhall.repository.SeatRepository;
import com.spacz.studyhall.repository.StudyHallRepository;
import com.spacz.studyhall.security.AuthenticatedUser;
import com.spacz.studyhall.service.StudyHallManagementService;
import com.spacz.studyhall.service.support.DefaultHours;
import com.spacz.studyhall.service.support.PageableSupport;
import com.spacz.studyhall.service.support.VendorAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyHallManagementServiceImpl implements StudyHallManagementService {

    private static final Set<String> SORTABLE = Set.of("name", "city", "createdAt", "updatedAt", "pricePerDay", "status");

    private final VendorAccess vendorAccess;
    private final StudyHallRepository studyHallRepository;
    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final AmenityRepository amenityRepository;
    private final ProgramRepository programRepository;
    private final HallViewAssembler assembler;
    private final StudyHallProperties properties;
    private final AuditEvents auditEvents;
    private final Clock clock;

    @Override
    @Transactional
    public StudyHallDetailResponse create(AuthenticatedUser vendor, CreateStudyHallRequest request) {
        VendorProfile profile = vendorAccess.writableVendor(vendor.userId());
        if (profile.getStatus() == VendorStatus.REJECTED) {
            throw new BusinessRuleException("VENDOR_REJECTED",
                    "Your vendor registration was rejected. Update your profile and submit it again.");
        }
        StudyHall hall = StudyHall.draft(profile);
        applyDetails(hall, request.name(), request.description(), request.addressLine(), request.city(),
                request.state(), request.pincode(), request.latitude(), request.longitude(), request.contactPhone(),
                request.contactEmail(), request.pricePerDay(), request.pricePerMonth(), request.rules());
        DefaultHours.seed(hall, request.openingTime(), request.closingTime());
        StudyHall saved = studyHallRepository.saveAndFlush(hall);
        auditEvents.record(vendor, "STUDY_HALL_CREATED", "STUDY_HALL", saved.getId(),
                "Created study hall '" + saved.getName() + "'");
        return assembler.detail(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudyHallSummaryResponse> listMine(Long vendorId, StudyHallStatus status, Pageable pageable) {
        vendorAccess.vendor(vendorId);
        Pageable safe = PageableSupport.restrictSort(pageable, SORTABLE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StudyHall> page = status == null
                ? studyHallRepository.findByVendor_VendorId(vendorId, safe)
                : studyHallRepository.findByVendor_VendorIdAndStatus(vendorId, status, safe);
        return assembler.page(page, null, null);
    }

    @Override
    @Transactional
    public StudyHallDetailResponse update(AuthenticatedUser vendor, Long studyHallId, UpdateStudyHallRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendor.userId(), studyHallId);
        applyDetails(hall, request.name(), request.description(), request.addressLine(), request.city(),
                request.state(), request.pincode(), request.latitude(), request.longitude(), request.contactPhone(),
                request.contactEmail(), request.pricePerDay(), request.pricePerMonth(), request.rules());
        auditEvents.record(vendor, "STUDY_HALL_UPDATED", "STUDY_HALL", studyHallId,
                "Updated study hall '" + hall.getName() + "'");
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public void delete(AuthenticatedUser vendor, Long studyHallId) {
        StudyHall hall = vendorAccess.writableHall(vendor.userId(), studyHallId);
        if (!hall.isDeletable()) {
            throw new BusinessRuleException("Only study halls that have not been approved yet can be deleted; "
                    + "set it INACTIVE instead");
        }
        if (bookingRepository.existsByStudyHallId(studyHallId)) {
            throw new BusinessRuleException("This study hall has booking history and cannot be deleted");
        }
        studyHallRepository.delete(hall);
        auditEvents.record(vendor, "STUDY_HALL_DELETED", "STUDY_HALL", studyHallId,
                "Deleted study hall '" + hall.getName() + "'");
    }

    @Override
    @Transactional
    public StudyHallDetailResponse submitForApproval(AuthenticatedUser vendor, Long studyHallId) {
        StudyHall hall = vendorAccess.writableHall(vendor.userId(), studyHallId);
        VendorStatus vendorStatus = hall.getVendor().getStatus();
        if (vendorStatus == VendorStatus.REJECTED || vendorStatus == VendorStatus.INACTIVE) {
            throw new BusinessRuleException("VENDOR_NOT_APPROVED",
                    "Study halls cannot be submitted while the vendor account is " + vendorStatus);
        }
        List<Seat> seats = seatRepository.findByStudyHallId(studyHallId);
        boolean bookableSeat = seats.stream().anyMatch(s -> s.getStatus() == SeatStatus.AVAILABLE
                && (s.effectiveDailyPrice() != null || s.effectiveMonthlyPrice() != null));
        if (!bookableSeat) {
            throw new BusinessRuleException("NO_SEATS", "Add at least one available seat with a price before submitting");
        }
        if (hall.getOperatingHours().stream().allMatch(OperatingHours::isClosed)) {
            throw new BusinessRuleException("NO_OPERATING_HOURS", "The study hall must be open at least one day a week");
        }
        hall.submitForApproval(properties.approvalRequired(), clock.instant());
        auditEvents.record(vendor, "STUDY_HALL_SUBMITTED", "STUDY_HALL", studyHallId,
                "Submitted study hall '" + hall.getName() + "' for approval");
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse changeVisibility(Long vendorId, Long studyHallId, StudyHallStatus status) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        hall.changeVisibility(status);
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    /**
     * Rows are updated in place per weekday (not delete + insert), which keeps the
     * (study_hall_id, day_of_week) unique constraint valid within the flush.
     */
    @Override
    @Transactional
    public StudyHallDetailResponse replaceOperatingHours(Long vendorId, Long studyHallId, OperatingHoursRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        Map<DayOfWeek, OperatingHoursRequest.DayHours> requested = request.days().stream()
                .collect(Collectors.toMap(OperatingHoursRequest.DayHours::dayOfWeek, Function.identity()));
        Map<DayOfWeek, OperatingHours> existing = hall.getOperatingHours().stream()
                .collect(Collectors.toMap(OperatingHours::getDayOfWeek, Function.identity()));
        for (DayOfWeek day : DayOfWeek.values()) {
            OperatingHoursRequest.DayHours wanted = requested.get(day);
            boolean closed = wanted == null || wanted.closed();
            OperatingHours row = existing.get(day);
            if (row == null) {
                row = OperatingHours.closed(hall, day);
                hall.getOperatingHours().add(row);
            }
            row.update(closed ? null : wanted.openTime(), closed ? null : wanted.closeTime(), closed);
        }
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse addImage(Long vendorId, Long studyHallId, ImageRequest request) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        List<StudyHallImage> images = hall.getImages();
        if (images.size() >= properties.maxImages()) {
            throw new BusinessRuleException("A study hall can have at most " + properties.maxImages() + " images");
        }
        boolean cover = request.cover() || images.isEmpty();
        if (cover) {
            images.forEach(image -> image.setCover(false));
        }
        int order = request.displayOrder() != null ? request.displayOrder() : images.size();
        images.add(StudyHallImage.of(hall, request.url().trim(), trimToNull(request.caption()), order, cover));
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse removeImage(Long vendorId, Long studyHallId, Long imageId) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        StudyHallImage image = hall.getImages().stream().filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Image", imageId));
        hall.getImages().remove(image);
        if (image.isCover() && !hall.getImages().isEmpty()) {
            hall.getImages().get(0).setCover(true);
        }
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse addAmenities(Long vendorId, Long studyHallId, Set<Long> amenityIds) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        hall.getAmenities().addAll(activeAmenities(amenityRepository, amenityIds));
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse removeAmenity(Long vendorId, Long studyHallId, Long amenityId) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        if (!hall.getAmenities().removeIf(a -> a.getId().equals(amenityId))) {
            throw new ResourceNotFoundException("Amenity " + amenityId + " is not assigned to this study hall");
        }
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse addPrograms(Long vendorId, Long studyHallId, Set<Long> programIds) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        List<Program> programs = programRepository.findByIdIn(programIds);
        Set<Long> found = programs.stream().filter(Program::isActive).map(Program::getId).collect(Collectors.toSet());
        Set<Long> missing = new TreeSet<>(programIds);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Unknown or inactive program IDs: " + missing);
        }
        hall.getPrograms().addAll(programs);
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    @Override
    @Transactional
    public StudyHallDetailResponse removeProgram(Long vendorId, Long studyHallId, Long programId) {
        StudyHall hall = vendorAccess.writableHall(vendorId, studyHallId);
        if (!hall.getPrograms().removeIf(p -> p.getId().equals(programId))) {
            throw new ResourceNotFoundException("Program " + programId + " is not assigned to this study hall");
        }
        return assembler.detail(studyHallRepository.saveAndFlush(hall));
    }

    /** All requested amenities must exist and be active. */
    static List<Amenity> activeAmenities(AmenityRepository repository, Set<Long> amenityIds) {
        if (amenityIds == null || amenityIds.isEmpty()) {
            return List.of();
        }
        List<Amenity> amenities = repository.findAllById(amenityIds);
        Set<Long> found = amenities.stream().filter(Amenity::isActive).map(Amenity::getId).collect(Collectors.toSet());
        Set<Long> missing = new TreeSet<>(amenityIds);
        missing.removeAll(found);
        if (!missing.isEmpty()) {
            throw new ResourceNotFoundException("Unknown or inactive amenity IDs: " + missing);
        }
        return amenities;
    }

    private static void applyDetails(StudyHall hall, String name, String description, String addressLine, String city,
                                     String state, String pincode, Double latitude, Double longitude,
                                     String contactPhone, String contactEmail, BigDecimal pricePerDay,
                                     BigDecimal pricePerMonth, String rules) {
        hall.setName(name.trim());
        hall.setDescription(trimToNull(description));
        hall.setAddressLine(addressLine.trim());
        hall.setCity(city.trim());
        hall.setState(state.trim());
        hall.setPincode(trimToNull(pincode));
        hall.setLatitude(latitude);
        hall.setLongitude(longitude);
        hall.setContactPhone(trimToNull(contactPhone));
        hall.setContactEmail(trimToNull(contactEmail));
        hall.setPricePerDay(pricePerDay);
        hall.setPricePerMonth(pricePerMonth);
        hall.setRules(trimToNull(rules));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
