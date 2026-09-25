package com.spacz.studyhall.mapper;

import com.spacz.studyhall.dto.hall.AmenityResponse;
import com.spacz.studyhall.dto.hall.BlockSummary;
import com.spacz.studyhall.dto.hall.ImageResponse;
import com.spacz.studyhall.dto.hall.OperatingHoursResponse;
import com.spacz.studyhall.dto.hall.ProgramRef;
import com.spacz.studyhall.dto.hall.StudyHallDetailResponse;
import com.spacz.studyhall.dto.hall.StudyHallSummaryResponse;
import com.spacz.studyhall.dto.program.ProgramResponse;
import com.spacz.studyhall.dto.vendor.VendorProfileResponse;
import com.spacz.studyhall.dto.vendor.VendorPublicResponse;
import com.spacz.studyhall.entity.Amenity;
import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.OperatingHours;
import com.spacz.studyhall.entity.Program;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallImage;
import com.spacz.studyhall.entity.VendorProfile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class StudyHallMapper {

    public VendorProfileResponse toResponse(VendorProfile v) {
        return new VendorProfileResponse(v.getId(), v.getVendorId(), v.getBusinessName(), v.getContactName(),
                v.getEmail(), v.getPhone(), v.getAddressLine(), v.getCity(), v.getState(), v.getPincode(),
                v.getGstNumber(), v.getDescription(), v.getLogoUrl(), v.getStatus(), v.getStatusReason(),
                v.missingForSubmission(), v.getSubmittedAt(), v.getApprovedAt(), v.getCreatedAt(), v.getUpdatedAt());
    }

    public VendorPublicResponse toPublic(VendorProfile v, long liveStudyHalls) {
        return new VendorPublicResponse(v.getVendorId(), v.displayName(), v.getCity(), v.getState(),
                v.getDescription(), v.getLogoUrl(), liveStudyHalls);
    }

    public AmenityResponse toResponse(Amenity a) {
        return new AmenityResponse(a.getId(), a.getCode(), a.getName(), a.getIcon(), a.getDescription(), a.isActive());
    }

    public List<AmenityResponse> amenities(Collection<Amenity> amenities) {
        return amenities.stream().sorted(Comparator.comparing(Amenity::getName)).map(this::toResponse).toList();
    }

    public ProgramResponse toResponse(Program p) {
        return new ProgramResponse(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getCategory(), p.isActive());
    }

    public ProgramRef toRef(Program p) {
        return p == null ? null : new ProgramRef(p.getId(), p.getCode(), p.getName());
    }

    public ImageResponse toResponse(StudyHallImage i) {
        return new ImageResponse(i.getId(), i.getUrl(), i.getCaption(), i.getDisplayOrder(), i.isCover());
    }

    public OperatingHoursResponse toResponse(OperatingHours h) {
        return new OperatingHoursResponse(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(), h.isClosed());
    }

    public BlockSummary toSummary(Block b) {
        return new BlockSummary(b.getId(), b.getName(), b.getTotalRows(), b.getTotalColumns(), b.getSeats().size(),
                b.effectiveDailyPrice(), b.effectiveMonthlyPrice(),
                b.getAmenities().stream().map(Amenity::getName).sorted().toList());
    }

    public StudyHallSummaryResponse toSummary(StudyHall h, long seatCount, Double distanceKm) {
        return new StudyHallSummaryResponse(h.getId(), h.getVendor().getVendorId(), h.getVendor().displayName(),
                h.getName(), h.getAddressLine(), h.getCity(), h.getState(), h.getLatitude(), h.getLongitude(),
                h.getPricePerDay(), h.getPricePerMonth(), h.getStatus(), coverUrl(h),
                h.getAmenities().stream().map(Amenity::getName).sorted().toList(),
                programs(h), seatCount, distanceKm);
    }

    public StudyHallDetailResponse toDetail(StudyHall h, long totalSeats) {
        return new StudyHallDetailResponse(h.getId(), h.getVendor().getVendorId(), h.getVendor().displayName(),
                h.getName(), h.getDescription(), h.getAddressLine(), h.getCity(), h.getState(), h.getPincode(),
                h.getLatitude(), h.getLongitude(), h.getContactPhone(), h.getContactEmail(), h.getPricePerDay(),
                h.getPricePerMonth(), h.getRules(), h.getStatus(), h.getStatusReason(),
                h.getImages().stream().map(this::toResponse).toList(),
                amenities(h.getAmenities()),
                programs(h),
                h.getOperatingHours().stream().sorted(Comparator.comparing(OperatingHours::getDayOfWeek))
                        .map(this::toResponse).toList(),
                h.getBlocks().stream().map(this::toSummary).toList(),
                totalSeats, h.getSubmittedAt(), h.getApprovedAt(), h.getCreatedAt(), h.getUpdatedAt());
    }

    private List<ProgramRef> programs(StudyHall h) {
        return h.getPrograms().stream()
                .sorted(Comparator.comparing(Program::getName))
                .map(this::toRef)
                .toList();
    }

    private static String coverUrl(StudyHall h) {
        return h.getImages().stream()
                .filter(StudyHallImage::isCover)
                .findFirst()
                .or(() -> h.getImages().stream().findFirst())
                .map(StudyHallImage::getUrl)
                .orElse(null);
    }
}
