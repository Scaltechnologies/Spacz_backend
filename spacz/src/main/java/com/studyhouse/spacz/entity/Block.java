package com.studyhouse.spacz.entity;

import jakarta.persistence.*;
import java.util.List;

@Entity
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long blockId; // Unique identifier for the block

    private String blockName; // Name of the block (e.g., "Block A", "Block 1")

    // Many-to-One relationship with Property (multiple blocks can belong to one property)
    @ManyToOne
    @JoinColumn(name = "property_id", referencedColumnName = "propertyId")
    private Property property;

    // One-to-Many relationship with Seat (a block can have many seats)
    @OneToMany(mappedBy = "block", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Seat> seats;  // Multiple seats can be in a block

    // Pricing fields for the block
    private double blockDailyPrice;   // Price per day for the block
    private double blockMonthlyPrice; // Price per month for the block

    // Getters and setters
    public Long getBlockId() {
        return blockId;
    }

    public void setBlockId(Long blockId) {
        this.blockId = blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public Property getProperty() {
        return property;
    }

    public void setProperty(Property property) {
        this.property = property;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void setSeats(List<Seat> seats) {
        this.seats = seats;
    }

    public double getBlockDailyPrice() {
        return blockDailyPrice;
    }

    public void setBlockDailyPrice(double blockDailyPrice) {
        this.blockDailyPrice = blockDailyPrice;
    }

    public double getBlockMonthlyPrice() {
        return blockMonthlyPrice;
    }

    public void setBlockMonthlyPrice(double blockMonthlyPrice) {
        this.blockMonthlyPrice = blockMonthlyPrice;
    }
}
