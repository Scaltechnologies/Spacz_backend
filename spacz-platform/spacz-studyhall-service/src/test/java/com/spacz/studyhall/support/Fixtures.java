package com.spacz.studyhall.support;

import com.spacz.studyhall.entity.Block;
import com.spacz.studyhall.entity.Seat;
import com.spacz.studyhall.entity.SeatType;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorProfile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * In-memory entity graphs for unit tests.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public static VendorProfile approvedVendor(long vendorId) {
        VendorProfile vendor = VendorProfile.draft(vendorId);
        vendor.setBusinessName("Focus Hall");
        vendor.setContactName("Owner");
        vendor.setPhone("+919999999999");
        vendor.setAddressLine("Main road");
        vendor.setCity("Hyderabad");
        vendor.submit(Instant.now());
        vendor.apply(StatusAction.APPROVE, null, Instant.now());
        ReflectionTestUtils.setField(vendor, "id", vendorId);
        return vendor;
    }

    public static StudyHall activeHall(long id, VendorProfile vendor, String pricePerDay, String pricePerMonth) {
        StudyHall hall = StudyHall.draft(vendor);
        ReflectionTestUtils.setField(hall, "id", id);
        hall.setName("Hall " + id);
        hall.setAddressLine("Main road");
        hall.setCity("Hyderabad");
        hall.setState("Telangana");
        hall.setPricePerDay(pricePerDay == null ? null : new BigDecimal(pricePerDay));
        hall.setPricePerMonth(pricePerMonth == null ? null : new BigDecimal(pricePerMonth));
        hall.submitForApproval(true, Instant.now());
        hall.apply(StatusAction.APPROVE, null, Instant.now());
        assert hall.getStatus() == StudyHallStatus.ACTIVE;
        return hall;
    }

    public static Block block(long id, StudyHall hall) {
        Block block = Block.of(hall, "Ground", 5, 5, 1);
        ReflectionTestUtils.setField(block, "id", id);
        hall.getBlocks().add(block);
        return block;
    }

    public static Seat seat(long id, Block block, String number) {
        Seat seat = Seat.of(block, number, 1, (int) (id % 5) + 1, SeatType.STANDARD);
        ReflectionTestUtils.setField(seat, "id", id);
        block.getSeats().add(seat);
        return seat;
    }
}
