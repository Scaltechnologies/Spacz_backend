package com.spacz.studyhall.notify;

import com.spacz.studyhall.config.BookingProperties;
import com.spacz.studyhall.entity.Booking;
import com.spacz.studyhall.entity.BookingStatus;
import com.spacz.studyhall.entity.Enrollment;
import com.spacz.studyhall.entity.StatusAction;
import com.spacz.studyhall.entity.StudyHall;
import com.spacz.studyhall.entity.StudyHallStatus;
import com.spacz.studyhall.entity.VendorProfile;
import com.spacz.studyhall.repository.BookingRepository;
import com.spacz.studyhall.service.impl.BookingActivityEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Every notification studyhall-service sends: approvals (to vendors), approval requests (to
 * admins), bookings and enrollments (to students and vendors), uploads and other vendor actions.
 * Called inside the business transaction; delivery happens after commit (see {@link NotificationForwarder}).
 */
@Component
@RequiredArgsConstructor
public class StudyHallNotifier {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final Notifications notifications;
    private final BookingRepository bookingRepository;
    private final BookingProperties bookingProperties;

    // ------------------------------------------------------------------ vendor approval

    /** The vendor profile went to PENDING: admins must review it. */
    public void vendorSubmitted(VendorProfile vendor) {
        notifications.send(NotificationEvent.toAdmins("REQUEST", "VENDOR_APPROVAL_REQUESTED",
                        "Partner awaiting approval", vendor.displayName() + " submitted their profile for review.")
                .about("VENDOR", vendor.getVendorId()).with("screen", "admin-vendor"));
        notifications.send(NotificationEvent.toUser(vendor.getVendorId(), "VENDOR", "APPROVAL", "VENDOR_SUBMITTED",
                        "Profile submitted for review",
                        "Thanks! Our team will review your partner profile shortly. We'll notify you once it's approved.")
                .about("VENDOR", vendor.getVendorId()).with("screen", "vendor-profile").inAppOnly());
    }

    public void vendorStatusChanged(VendorProfile vendor, StatusAction action, String reason) {
        NotificationEvent event = switch (action) {
            case APPROVE -> NotificationEvent.toUser(vendor.getVendorId(), "VENDOR", "APPROVAL", "VENDOR_APPROVED",
                    "Your partner account is approved",
                    "Welcome aboard! Your approved study halls are now live and students can book seats.");
            case REJECT -> NotificationEvent.toUser(vendor.getVendorId(), "VENDOR", "APPROVAL", "VENDOR_REJECTED",
                    "Partner application not approved", withReason("Your partner profile was not approved.", reason));
            case SUSPEND -> NotificationEvent.toUser(vendor.getVendorId(), "VENDOR", "ACCOUNT", "VENDOR_SUSPENDED",
                    "Partner account suspended", withReason(
                            "Your study halls are hidden from students until the account is re-activated.", reason));
            case ACTIVATE -> NotificationEvent.toUser(vendor.getVendorId(), "VENDOR", "ACCOUNT", "VENDOR_ACTIVATED",
                    "Partner account active again", "Your account is active and your approved study halls are live again.");
        };
        notifications.send(event.about("VENDOR", vendor.getVendorId()).with("screen", "vendor-profile"));
    }

    // ------------------------------------------------------------------ study hall approval

    /** After {@code hall.submitForApproval}: admins review it, or it went live directly (approval not required). */
    public void hallSubmitted(StudyHall hall) {
        Long vendorId = hall.getVendor().getVendorId();
        if (hall.getStatus() == StudyHallStatus.PENDING_APPROVAL) {
            notifications.send(NotificationEvent.toAdmins("REQUEST", "STUDY_HALL_APPROVAL_REQUESTED",
                            "Study hall awaiting approval",
                            "'" + hall.getName() + "' by " + hall.getVendor().displayName() + " is waiting for review.")
                    .about("STUDY_HALL", hall.getId()).with("screen", "admin-studyhall"));
            notifications.send(NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_SUBMITTED",
                            "Study hall submitted", "'" + hall.getName() + "' was sent for review. We'll let you know once it's approved.")
                    .about("STUDY_HALL", hall.getId()).with("screen", "vendor-studyhall").inAppOnly());
        } else {
            notifications.send(NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_PUBLISHED",
                            "Study hall published", liveMessage(hall))
                    .about("STUDY_HALL", hall.getId()).with("screen", "vendor-studyhall"));
        }
    }

    public void hallStatusChanged(StudyHall hall, StatusAction action, String reason) {
        Long vendorId = hall.getVendor().getVendorId();
        String name = "'" + hall.getName() + "'";
        NotificationEvent event = switch (action) {
            case APPROVE -> NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_APPROVED",
                    name + " is approved", liveMessage(hall));
            case REJECT -> NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_REJECTED",
                    name + " was not approved", withReason("Please update the study hall and submit it again.", reason));
            case SUSPEND -> NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_SUSPENDED",
                    name + " is suspended", withReason("Students cannot find or book it until it is re-activated.", reason));
            case ACTIVATE -> NotificationEvent.toUser(vendorId, "VENDOR", "APPROVAL", "STUDY_HALL_ACTIVATED",
                    name + " is active again", liveMessage(hall));
        };
        notifications.send(event.about("STUDY_HALL", hall.getId()).with("screen", "vendor-studyhall"));
    }

    // ------------------------------------------------------------------ vendor's own actions (in-app)

    public void hallSaved(StudyHall hall, boolean created) {
        notifications.send(NotificationEvent.toUser(hall.getVendor().getVendorId(), "VENDOR", "ACTIVITY",
                        created ? "STUDY_HALL_CREATED" : "STUDY_HALL_UPDATED",
                        created ? "Study hall created" : "Study hall updated",
                        "'" + hall.getName() + "' was " + (created ? "created." : "saved."))
                .about("STUDY_HALL", hall.getId()).with("screen", "vendor-studyhall").inAppOnly());
    }

    public void hallDeleted(Long vendorId, Long hallId, String name) {
        notifications.send(NotificationEvent.toUser(vendorId, "VENDOR", "ACTIVITY", "STUDY_HALL_DELETED",
                "Study hall deleted", "'" + name + "' was deleted.").about("STUDY_HALL", hallId).inAppOnly());
    }

    public void hallImageUploaded(StudyHall hall) {
        notifications.send(NotificationEvent.toUser(hall.getVendor().getVendorId(), "VENDOR", "UPLOAD",
                        "STUDY_HALL_IMAGE_UPLOADED", "Photo uploaded",
                        "A photo was added to '" + hall.getName() + "' (" + hall.getImages().size() + " in total).")
                .about("STUDY_HALL", hall.getId()).with("screen", "vendor-studyhall").inAppOnly());
    }

    // ------------------------------------------------------------------ enrollments

    /** A vendor added a registered student (walk-ins without an account get nothing). */
    public void studentEnrolled(Enrollment enrollment) {
        if (enrollment.getUserId() == null) {
            return;
        }
        StudyHall hall = enrollment.getStudyHall();
        String seat = enrollment.getSeat() != null ? ", seat " + enrollment.getSeat().getSeatNumber() : "";
        notifications.send(NotificationEvent.toUser(enrollment.getUserId(), "USER", "ENROLLMENT", "ENROLLED",
                        "You're enrolled at " + hall.getName(),
                        hall.getName() + " added you as a student from " + DATE.format(enrollment.getStartDate()) + seat + ".")
                .about("ENROLLMENT", enrollment.getId()).with("screen", "enrollments").with("studyHallId", hall.getId()));
    }

    // ------------------------------------------------------------------ bookings

    /** Booking events are published inside the booking transaction, so the booking can be read here. */
    @EventListener
    public void onBookingActivity(BookingActivityEvent event) {
        bookingRepository.findDetailed(event.bookingId()).ifPresent(booking -> bookingChanged(booking, event.activityType()));
    }

    void bookingChanged(Booking booking, String activityType) {
        StudyHall hall = booking.getStudyHall();
        Long vendorId = hall.getVendor().getVendorId();
        String seat = "Seat " + booking.getSeat().getSeatNumber();
        String dates = dates(booking.getStartDate(), booking.getEndDate());
        String ref = booking.getBookingReference();
        switch (activityType) {
            case "BOOKING_CREATED" -> {
                if (booking.getStatus() == BookingStatus.PENDING && booking.getHoldExpiresAt() != null) {
                    String until = TIME.format(booking.getHoldExpiresAt().atZone(bookingProperties.zone()));
                    student(booking, "BOOKING", "BOOKING_HELD", "Seat held for you",
                            seat + " at " + hall.getName() + " is held until " + until + ". Complete your payment to keep it.");
                }
            }
            case "BOOKING_CONFIRMED" -> {
                student(booking, "BOOKING", "BOOKING_CONFIRMED", "Booking confirmed",
                        seat + " at " + hall.getName() + ", " + dates + ". Booking ref " + ref + ".");
                vendor(booking, vendorId, "BOOKING_RECEIVED", "New booking at " + hall.getName(),
                        seat + " booked for " + dates + " (" + booking.getPlan().name().toLowerCase(Locale.ENGLISH)
                                + "). Ref " + ref + ".");
            }
            case "BOOKING_CANCELLED" -> {
                student(booking, "BOOKING", "BOOKING_CANCELLED", "Booking cancelled",
                        "Your booking " + ref + " for " + seat.toLowerCase(Locale.ENGLISH) + " at " + hall.getName()
                                + " was cancelled.", false);
                if (booking.getConfirmedAt() != null) {
                    vendor(booking, vendorId, "BOOKING_CANCELLED", "Booking cancelled at " + hall.getName(),
                            seat + " is free again for " + dates + ". Ref " + ref + ".");
                }
            }
            case "BOOKING_EXPIRED" -> student(booking, "BOOKING", "BOOKING_EXPIRED", "Seat hold expired",
                    seat + " at " + hall.getName() + " was released because the booking was not confirmed in time. "
                            + "You can book again anytime.");
            case "BOOKING_COMPLETED" -> student(booking, "BOOKING", "BOOKING_COMPLETED", "Booking completed",
                    "Hope your sessions at " + hall.getName() + " went well! Book again whenever you're ready.", false);
            default -> {
                // no notification for other booking activity
            }
        }
    }

    private void student(Booking booking, String category, String type, String title, String body) {
        student(booking, category, type, title, body, true);
    }

    private void student(Booking booking, String category, String type, String title, String body, boolean push) {
        NotificationEvent event = NotificationEvent.toUser(booking.getUserId(), "USER", category, type, title, body)
                .about("BOOKING", booking.getId()).with("screen", "booking").with("bookingId", booking.getId());
        notifications.send(push ? event : event.inAppOnly());
    }

    private void vendor(Booking booking, Long vendorId, String type, String title, String body) {
        notifications.send(NotificationEvent.toUser(vendorId, "VENDOR", "BOOKING", type, title, body)
                .about("BOOKING", booking.getId()).with("screen", "vendor-booking").with("bookingId", booking.getId())
                .with("studyHallId", booking.getStudyHall().getId()));
    }

    // ------------------------------------------------------------------ text helpers

    private static String liveMessage(StudyHall hall) {
        return hall.getStatus() == StudyHallStatus.ACTIVE
                ? "'" + hall.getName() + "' is now live. Students can find it and book seats."
                : "'" + hall.getName() + "' will go live as soon as your partner account is approved.";
    }

    private static String withReason(String message, String reason) {
        return reason == null || reason.isBlank() ? message : message + " Reason: " + reason.trim();
    }

    private static String dates(LocalDate start, LocalDate end) {
        return start.equals(end) ? DATE.format(start) : DATE.format(start) + " – " + DATE.format(end);
    }
}
