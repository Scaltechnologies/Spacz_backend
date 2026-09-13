package com.studyhouse.spacz.entity;

import jakarta.persistence.*;

@Entity
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId; // Unique identifier for the seat

    private String seatNumber; // Seat number within the block (e.g., "A1", "B3")

    // Many-to-One relationship with Block (many seats belong to one block)
    @ManyToOne
    @JoinColumn(name = "block_id", referencedColumnName = "blockId")
    private Block block;

    private boolean isReserved; // Whether the seat is reserved or not

    // One-to-One relationship with Booking (one seat can only have one booking at a time)
    @OneToOne(mappedBy = "seat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Booking booking; // Each seat can have one booking

    // Pricing field for the seat (could be different from block pricing)
    private double seatPrice; // Price for this specific seat

    // Getters and setters
    public Long getSeatId() {
        return seatId;
    }

    public void setSeatId(Long seatId) {
        this.seatId = seatId;
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }

    public Block getBlock() {
        return block;
    }

    public void setBlock(Block block) {
        this.block = block;
    }

    public boolean isReserved() {
        return isReserved;
    }

    public void setReserved(boolean reserved) {
        isReserved = reserved;
    }

    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking booking) {
        this.booking = booking;
    }

    public double getSeatPrice() {
        return seatPrice;
    }

    public void setSeatPrice(double seatPrice) {
        this.seatPrice = seatPrice;
    }
}
