package com.studyhouse.spacz.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId; // Unique identifier for the booking

    private LocalDate startDate;  // Start date of the booking
    private LocalDate endDate;    // End date of the booking

    // One-to-One relationship with Seat (one booking can only be for one seat)
    @OneToOne
    @JoinColumn(name = "seat_id", referencedColumnName = "seatId")
    private Seat seat; // The seat being booked

    // Many-to-One relationship with AspirantUser (many bookings can be made by one aspirant user)
    @ManyToOne
    @JoinColumn(name = "aspirant_user_id", referencedColumnName = "aspirantUserId")
    private AspirantUser aspirantUser; // The aspirant user making the booking

    // Getters and setters
    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Seat getSeat() {
        return seat;
    }

    public void setSeat(Seat seat) {
        this.seat = seat;
    }

    public AspirantUser getAspirantUser() {
        return aspirantUser;
    }

    public void setAspirantUser(AspirantUser aspirantUser) {
        this.aspirantUser = aspirantUser;
    }
}
